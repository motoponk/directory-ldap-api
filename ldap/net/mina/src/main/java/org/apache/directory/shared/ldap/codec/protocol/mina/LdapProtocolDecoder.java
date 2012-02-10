/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 * 
 */
package org.apache.directory.shared.ldap.codec.protocol.mina;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.directory.shared.asn1.DecoderException;
import org.apache.directory.shared.asn1.ber.Asn1Decoder;
import org.apache.directory.shared.asn1.ber.tlv.TLVStateEnum;
import org.apache.directory.shared.ldap.codec.api.LdapDecoder;
import org.apache.directory.shared.ldap.codec.api.LdapMessageContainer;
import org.apache.directory.shared.ldap.codec.api.MessageDecorator;
import org.apache.directory.shared.ldap.codec.api.ResponseCarryingException;
import org.apache.directory.shared.ldap.model.exception.ResponseCarryingMessageException;
import org.apache.directory.shared.ldap.model.message.Message;
import org.apache.directory.shared.util.Strings;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A LDAP message decoder. It is based on shared-ldap decoder.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class LdapProtocolDecoder implements ProtocolDecoder
{
    /** The logger */
    private static Logger LOG = LoggerFactory.getLogger( LdapDecoder.class );
    
    /** A speedup for logger */
    private static final boolean IS_DEBUG = LOG.isDebugEnabled();

    /** The ASN 1 decoder instance */
    private Asn1Decoder asn1Decoder;


    /**
     * Creates a new instance of LdapProtocolEncoder.
     *
     * @param codec The LDAP codec service associated with this encoder.
     */
    public LdapProtocolDecoder()
    {
        asn1Decoder = new Asn1Decoder();
    }


    /**
     * {@inheritDoc}
     */
    public void decode( IoSession session, IoBuffer in, ProtocolDecoderOutput out ) throws Exception
    {
        @SuppressWarnings("unchecked")
        LdapMessageContainer<MessageDecorator<? extends Message>> messageContainer =
            ( LdapMessageContainer<MessageDecorator<? extends Message>> )
            session.getAttribute( LdapDecoder.MESSAGE_CONTAINER_ATTR );

        if ( session.containsAttribute( LdapDecoder.MAX_PDU_SIZE_ATTR ) )
        {
            int maxPDUSize = ( Integer ) session.getAttribute( LdapDecoder.MAX_PDU_SIZE_ATTR );

            messageContainer.setMaxPDUSize( maxPDUSize );
        }

        List<Message> decodedMessages = new ArrayList<Message>();
        ByteBuffer buf = in.buf();

        decode( buf, messageContainer, decodedMessages );

        for ( Message message : decodedMessages )
        {
            out.write( message );
        }
    }


    /**
     * Decode an incoming buffer into LDAP messages. The result can be 0, 1 or many
     * LDAP messages, which will be stored into the array the caller has created.
     * 
     * @param buffer The incoming byte buffer
     * @param messageContainer The LdapMessageContainer which will be used to store the
     * message being decoded. If the message is not fully decoded, the ucrrent state
     * is stored into this container
     * @param decodedMessages The list of decoded messages
     * @throws Exception If the decoding failed
     */
    private void decode( ByteBuffer buffer, LdapMessageContainer<MessageDecorator<? extends Message>> messageContainer,
        List<Message> decodedMessages ) throws DecoderException
    {
        buffer.mark();

        while ( buffer.hasRemaining() )
        {
            try
            {
                if ( IS_DEBUG )
                {
                    LOG.debug( "Decoding the PDU : " );

                    int size = buffer.limit();
                    int position = buffer.position();
                    int pduLength = size - position;

                    byte[] array = new byte[pduLength];

                    System.arraycopy( buffer.array(), position, array, 0, pduLength );

                    buffer.position( size );

                    if ( array.length == 0 )
                    {
                        LOG.debug( "NULL buffer, what the HELL ???" );
                    }
                    else
                    {
                        LOG.debug( Strings.dumpBytes( array ) );
                    }

                    buffer.reset();
                }

                asn1Decoder.decode( buffer, messageContainer );

                if ( messageContainer.getState() == TLVStateEnum.PDU_DECODED )
                {
                    if ( IS_DEBUG )
                    {
                        LOG.debug( "Decoded LdapMessage : " + messageContainer.getMessage() );
                    }

                    Message message = messageContainer.getMessage();

                    decodedMessages.add( message );

                    messageContainer.clean();
                }
            }
            catch ( DecoderException de )
            {
                buffer.clear();
                messageContainer.clean();

                if ( de instanceof ResponseCarryingException )
                {
                    // Transform the DecoderException message to a MessageException
                    ResponseCarryingMessageException rcme = new ResponseCarryingMessageException( de.getMessage() );
                    rcme.setResponse( ( ( ResponseCarryingException ) de ).getResponse() );

                    throw rcme;
                }
                else
                {
                    // TODO : This is certainly not the way we should handle such an exception !
                    throw new ResponseCarryingException( de.getMessage() );
                }
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    public void finishDecode( IoSession session, ProtocolDecoderOutput out ) throws Exception
    {
        // Nothing to do
    }


    /**
     * {@inheritDoc}
     */
    public void dispose( IoSession session ) throws Exception
    {
        // Nothing to do
    }
}
