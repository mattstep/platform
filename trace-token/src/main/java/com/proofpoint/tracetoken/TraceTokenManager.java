package com.proofpoint.tracetoken;

import com.google.inject.Singleton;
import com.proofpoint.log.Logger;

import java.util.UUID;

@Singleton
public class TraceTokenManager
{
    private final ThreadLocal<String> token = new ThreadLocal<String>();

    public void registerRequestToken(String token)
    {
        this.token.set(token);
        Logger.setTraceToken(token);
    }

    public String getCurrentRequestToken()
    {
        return this.token.get();
    }

    public String createAndRegisterNewRequestToken()
    {
        String newToken = UUID.randomUUID().toString();
        this.token.set(newToken);
        Logger.setTraceToken(newToken);

        return newToken;
    }
}
