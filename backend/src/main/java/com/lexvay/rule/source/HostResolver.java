package com.lexvay.rule.source;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Name resolution behind a seam, so the address policy is tested without DNS. */
public interface HostResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;
}
