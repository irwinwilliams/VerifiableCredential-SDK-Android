package com.microsoft.did.sdk.registrars

import com.microsoft.did.sdk.identifier.IdentifierDocument
import io.ktor.client.engine.HttpClientEngine

/**
 * @interface defining methods and properties
 * to be implemented by specific registration methods.
 */
interface IRegistrar {

    /**
     * Registers the identifier document on the ledger
     * returning the identifier generated by the registrar.
     * @param identifierDocument to be registered.
     * @param signingKeyReference reference to the key to be used for signing request.
     * @return IdentifierDocument that was registered.
     * @throws Error if unable to register Identifier Document.
     */
    open suspend fun register(): Any
}