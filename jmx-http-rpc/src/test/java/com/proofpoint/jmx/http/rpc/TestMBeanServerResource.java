package com.proofpoint.jmx.http.rpc;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.http.server.TheServlet;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.MBeanExporter;
import org.weakref.jmx.Managed;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.UUID;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import static org.weakref.jmx.ObjectNames.generatedNameOf;

public class TestMBeanServerResource
{
    private TestingHttpServer server;
    private MBeanServerConnection mbeanServerConnection;
    private MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
    private TestMBean testMBean;
    private ObjectName testMBeanName;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                new TestingNodeModule(),
                new TestingHttpServerModule(),
                new JsonModule(),
                new JmxHttpRpcModule(TheServlet.class),
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(MBeanServer.class).toInstance(platformMBeanServer);
                        binder.bind(TestMBean.class).in(Scopes.SINGLETON);
                    }
                });

        server = injector.getInstance(TestingHttpServer.class);
        server.start();

        testMBean = injector.getInstance(TestMBean.class);
        testMBeanName = new ObjectName(generatedNameOf(TestMBean.class));
        MBeanExporter exporter = new MBeanExporter(platformMBeanServer);
        exporter.export(testMBeanName.toString(), testMBean);

        JMXConnector connect = JMXConnectorFactory.connect(
                new JMXServiceURL("service:jmx:" + server.getBaseUrl()),
                ImmutableMap.of(JMXConnector.CREDENTIALS, new String[] {"foo", "bar"}));
        mbeanServerConnection = connect.getMBeanServerConnection();

    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        if (server != null) {
            server.stop();
            platformMBeanServer.unregisterMBean(testMBeanName);
        }
    }

    @Test
    public void testGetMBeanCount()
            throws Exception
    {
        assertEquals(mbeanServerConnection.getMBeanCount(), platformMBeanServer.getMBeanCount());
    }

    @Test
    public void testIsRegistered()
            throws Exception
    {
        assertEquals(mbeanServerConnection.isRegistered(testMBeanName), true);
        assertEquals(mbeanServerConnection.isRegistered(new ObjectName("fake", "fake", "fake")), false);
    }

    @Test
    public void testIsInstanceOf()
            throws Exception
    {
        assertEquals(mbeanServerConnection.isInstanceOf(testMBeanName, TestMBean.class.getName()), true);
        assertEquals(mbeanServerConnection.isInstanceOf(testMBeanName, Object.class.getName()), true);
        assertEquals(mbeanServerConnection.isInstanceOf(testMBeanName, UUID.class.getName()), false);
    }

    @Test
    public void testGetDefaultDomain()
            throws Exception
    {
        assertEquals(mbeanServerConnection.getDefaultDomain(), platformMBeanServer.getDefaultDomain());
    }

    @Test
    public void testGetDomains()
            throws Exception
    {
        assertEquals(mbeanServerConnection.getDomains(), platformMBeanServer.getDomains());
    }

    @Test
    public void testGetObjectInstance()
            throws Exception
    {
        assertEquals(mbeanServerConnection.getObjectInstance(testMBeanName), platformMBeanServer.getObjectInstance(testMBeanName));
    }

    @Test
    public void testGetMBeanInfo()
            throws Exception
    {
        assertEquals(mbeanServerConnection.getMBeanInfo(testMBeanName), platformMBeanServer.getMBeanInfo(testMBeanName));
    }

    @Test
    public void testGetQueryMBeanNames()
            throws Exception
    {
        assertEquals(mbeanServerConnection.queryNames(testMBeanName, null), platformMBeanServer.queryNames(testMBeanName, null));
        assertEquals(mbeanServerConnection.queryNames(new ObjectName("*:*"), null), platformMBeanServer.queryNames(new ObjectName("*:*"), null));
    }

    @Test
    public void testGetQueryMBeans()
            throws Exception
    {
        assertEquals(mbeanServerConnection.queryMBeans(testMBeanName, null), platformMBeanServer.queryMBeans(testMBeanName, null));
        assertEquals(mbeanServerConnection.queryMBeans(new ObjectName("*:*"), null), platformMBeanServer.queryMBeans(new ObjectName("*:*"), null));
    }

    @Test
    public void testGetAttribute()
            throws Exception
    {
        assertEquals(mbeanServerConnection.getAttribute(testMBeanName, "Value"), null);
        testMBean.setValue("FOO");
        assertEquals(mbeanServerConnection.getAttribute(testMBeanName, "Value"), "FOO");

        assertEquals(mbeanServerConnection.getAttribute(testMBeanName, "ObjectValue"), null);
        testMBean.setObjectValue(UUID.randomUUID());
        assertEquals(mbeanServerConnection.getAttribute(testMBeanName, "ObjectValue"), testMBean.getObjectValue());
    }

    @Test
    public void testGetAttributes()
            throws Exception
    {
        assertEquals(mbeanServerConnection.getAttributes(testMBeanName, new String[]{"Value", "ObjectValue"}),
                new AttributeList(ImmutableList.of(new Attribute("Value", null), new Attribute("ObjectValue", null))));

        testMBean.setValue("FOO");
        testMBean.setObjectValue(UUID.randomUUID());

        assertEquals(mbeanServerConnection.getAttributes(testMBeanName, new String[]{"Value", "ObjectValue"}),
                new AttributeList(ImmutableList.of(new Attribute("Value", "FOO"), new Attribute("ObjectValue", testMBean.getObjectValue()))));
    }

    @Test
    public void testSetAttribute()
            throws Exception
    {
        mbeanServerConnection.setAttribute(testMBeanName, new Attribute("Value", "Foo"));
        assertEquals(testMBean.getValue(), "Foo");
        mbeanServerConnection.setAttribute(testMBeanName, new Attribute("Value", null));
        assertEquals(testMBean.getValue(), null);

        UUID uuid = UUID.randomUUID();
        mbeanServerConnection.setAttribute(testMBeanName, new Attribute("ObjectValue", uuid));
        assertEquals(testMBean.getObjectValue(), uuid);
        mbeanServerConnection.setAttribute(testMBeanName, new Attribute("ObjectValue", null));
        assertEquals(testMBean.getObjectValue(), null);
    }

    @Test
    public void testSetAttributes()
            throws Exception
    {
        UUID uuid = UUID.randomUUID();
        mbeanServerConnection.setAttributes(testMBeanName, new AttributeList(ImmutableList.of(new Attribute("Value", "Foo"), new Attribute("ObjectValue", uuid))));
        assertEquals(testMBean.getValue(), "Foo");
        assertEquals(testMBean.getObjectValue(), uuid);

        mbeanServerConnection.setAttributes(testMBeanName, new AttributeList(ImmutableList.of(new Attribute("Value", null), new Attribute("ObjectValue", null))));
        assertEquals(testMBean.getValue(), null);
        assertEquals(testMBean.getObjectValue(), null);
    }

    @Test
    public void testInvoke()
            throws Exception
    {
        assertEquals(testMBean.noArgsMethodInvoked, false);
        mbeanServerConnection.invoke(testMBeanName, "noArgsMethod", null, null);
        assertEquals(testMBean.noArgsMethodInvoked, true);

        UUID uuid = UUID.randomUUID();
        assertEquals(mbeanServerConnection.invoke(testMBeanName, "echo", new Object[]{uuid}, new String[]{Object.class.getName()}), uuid);
    }

    @Test
    public void testInvokeThrows()
            throws Exception
    {
        try {
            mbeanServerConnection.invoke(testMBeanName, "throwException", new Object[]{new Exception("exception-message")}, new String[]{Throwable.class.getName()});
            fail("Expected exception");
        }
        catch (MBeanException e) {
            assertTrue(e.getCause() instanceof Exception);
            assertEquals(e.getCause().getMessage(), "exception-message");
        }
    }

    public static class TestMBean
    {
        private String value;
        private Object objectValue;
        public boolean noArgsMethodInvoked;

        @Managed
        public String getValue()
        {
            return value;
        }

        @Managed
        public void setValue(String value)
        {
            this.value = value;
        }

        @Managed
        public Object getObjectValue()
        {
            return objectValue;
        }

        @Managed
        public void setObjectValue(Object objectValue)
        {
            this.objectValue = objectValue;
        }

        @Managed
        public void noArgsMethod()
        {
            noArgsMethodInvoked = true;
        }

        @Managed
        public Object echo(Object object)
        {
            return object;
        }

        @Managed
        public void throwException(Throwable t)
                throws Throwable
        {
            throw t;
        }
    }
}
