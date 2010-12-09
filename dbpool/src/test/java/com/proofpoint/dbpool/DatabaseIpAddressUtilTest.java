package com.proofpoint.dbpool;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.proofpoint.dbpool.DatabaseIpAddressUtil.toDatabaseIpAddress;
import static com.proofpoint.dbpool.DatabaseIpAddressUtil.fromDatabaseIpAddress;

public class DatabaseIpAddressUtilTest
{
    @Test
    public void test()
            throws Exception
    {
        verifyIpAddressConversion("0.0.0.0", 0, Integer.MIN_VALUE);
        verifyIpAddressConversion("255.255.255.255", -1, Integer.MAX_VALUE);
        verifyIpAddressConversion("128.0.0.0", Integer.MIN_VALUE, 0);
        verifyIpAddressConversion("127.255.255.255", Integer.MAX_VALUE, -1);
    }

    private void verifyIpAddressConversion(String ipString, int expectedJavaIpAddress, int expectedDatabaseIpAddress)
            throws UnknownHostException
    {
        // use Java to convert the string to an integer
        InetAddress address = InetAddress.getByName(ipString);
        // java inet4 address hashcode is the address object
        int javaIpAddress = address.hashCode();

        // Verify the java integer value is as expected
        assertEquals(javaIpAddress, expectedJavaIpAddress);

        // Convert to database style and verify
        int databaseIpAddress = toDatabaseIpAddress(javaIpAddress);
        assertEquals(databaseIpAddress, expectedDatabaseIpAddress);

        // Finally, roundtrip back to java and verify
        assertEquals(fromDatabaseIpAddress(databaseIpAddress), javaIpAddress);
    }
}