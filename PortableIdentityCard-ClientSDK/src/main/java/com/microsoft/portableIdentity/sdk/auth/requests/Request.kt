package com.microsoft.portableIdentity.sdk.auth.requests

import com.microsoft.portableIdentity.sdk.auth.credentialRequests.CredentialRequests

interface Request {

    /**
     * Get Credential Requests if there are any in Request.
     *
     * @return credentials requests if exist, null if no credentials requested.
     */
    fun getCredentialRequests(): CredentialRequests?
}