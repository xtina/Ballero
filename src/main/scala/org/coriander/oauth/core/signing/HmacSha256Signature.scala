/*
Copyright 2011 Ben Biddington

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.coriander.oauth.core.cryptography.signing

import com.cldellow.ballero.data.Base64
import org.coriander.oauth.core.uri._
import org.coriander.oauth.core.CredentialSet
import org.coriander.oauth.core.cryptography._
import org.coriander.oauth.core.signing.Signature

// TODO: Do we need the sha256 abstraction any longer?
class HmacSha256Signature(urlEncoder : UrlEncoder, credentials : CredentialSet) extends Signature {
    def this(
        urlEncoder  : UrlEncoder,
        credentials : CredentialSet,
        sha256   		: Sha256
    ) {
        this(urlEncoder, credentials)
        this.sha256 = sha256
    }

    def this(credentials : CredentialSet) = this(
		new OAuthUrlEncoder,
		credentials
	)

    def sign(baseString : String) = {
        validate
		
        getSignature(baseString);
    }

    private def getSignature(baseString : String) : String = {
		val signature = sha256.create(formatKey, baseString)
    Base64.encode(signature)
    }

    // See: http://oauth.net/core/1.0, section 9.2
    private def formatKey : String = {
        %%(getConsumerSecret) + "&" + %%(getTokenSecret)
    }

    private def getConsumerSecret : String = {
        if (credentials hasConsumer) credentials.consumer.secret else null
    }

    private def getTokenSecret : String = {
    	if (credentials hasToken) credentials.token.secret else DEFAULT_TOKEN_SECRET
    }

    private def %% (value : String) : String = urlEncoder.encode(value)

    private def validate {
        requireUrlEncoder
        validateConsumerCredential
        validateToken
    }

    private def requireUrlEncoder {
        require (urlEncoder != null, "Please supply a UrlEncoder.")
    }

    private def validateConsumerCredential {
        require (credentials.hasConsumer, "The supplied 'credentials' is missing a consumer.")
		require (credentials.consumer.key != null, "The supplied consumer has no key defined.")
		require (credentials.consumer.secret != null, "The supplied consumer has no secret defined.")
    }

    private def validateToken {
        if (credentials.hasToken) {
			require(credentials.token.secret != null, "The supplied token is missing a secret.")
		} 
    }

	private val DEFAULT_TOKEN_SECRET 	= ""
	private var sha256 : Sha256 			= new HmacSha256
    private val encoding 				= org.apache.http.protocol.HTTP.UTF_8
}
