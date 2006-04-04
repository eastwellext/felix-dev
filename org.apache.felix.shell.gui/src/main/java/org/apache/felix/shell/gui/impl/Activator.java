/*
 * Oscar Shell GUI
 * Copyright (c) 2004, Richard S. Hall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of the ungoverned.org nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Contact: Richard S. Hall (heavy@ungoverned.org)
 * Contributor(s):
 *
**/
package org.apache.felix.shell.gui.impl;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.event.EventListenerList;

import org.osgi.framework.*;
import org.apache.felix.shell.gui.Plugin;

public class Activator implements BundleActivator
{
    private BundleContext m_context = null;
    private List m_pluginList = null;
    private EventListenerList m_listenerList = null;
    private JFrame m_frame = null;

    public static final String PLUGIN_LIST_PROPERTY = "pluginList";

    public Activator()
    {
        m_pluginList = new ArrayList();
        m_listenerList = new EventListenerList();
    }

    public synchronized int getPluginCount()
    {
        if (m_pluginList == null)
        {
            return 0;
        }
        return m_pluginList.size();
    }

    public synchronized Plugin getPlugin(int i)
    {
        if ((i < 0) || (i >= getPluginCount()))
        {
            return null;
        }
        return (Plugin) m_pluginList.get(i);
    }

    public synchronized boolean pluginExists(Plugin plugin)
    {
        for (int i = 0; i < m_pluginList.size(); i++)
        {
            if (m_pluginList.get(i) == plugin)
            {
                return true;
            }
        }
        return false;
    }

    //
    // Bundle activator methods.
    //

    public void start(BundleContext context)
    {
        m_context = context;

        // Listen for factory service events.
        ServiceListener sl = new ServiceListener() {
            public void serviceChanged(ServiceEvent event)
            {
                ServiceReference ref = event.getServiceReference();
                Object svcObj = m_context.getService(ref);
                if ((event.getType() == ServiceEvent.REGISTERED) &&
                    (svcObj instanceof Plugin))
                {
                    synchronized (Activator.this)
                    {
                        // Check for duplicates.
                        if (!m_pluginList.contains(svcObj))
                        {
                            m_pluginList.add(svcObj);
                            firePropertyChangedEvent(
                                PLUGIN_LIST_PROPERTY, null, null);
                        }
                    }
                }
                else if ((event.getType() == ServiceEvent.UNREGISTERING) &&
                    (svcObj instanceof Plugin))
                {
                    synchronized (Activator.this)
                    {
                        m_pluginList.remove(svcObj);
                        firePropertyChangedEvent(
                            PLUGIN_LIST_PROPERTY, null, null);
                    }
                }
                else
                {
                    m_context.ungetService(ref);
                }
            }
        };
        try
        {
            m_context.addServiceListener(sl,
                "(objectClass="
                + org.apache.felix.shell.gui.Plugin.class.getName()
                + ")");
        }
        catch (InvalidSyntaxException ex)
        {
            System.err.println("ShellGuiActivator: Cannot add service listener.");
            System.err.println("ShellGuiActivator: " + ex);
        }

        // Now try to manually initialize the plugin list
        // since some might already be available.
        initializePlugins();

        // Create and display the frame.
        if (m_frame == null)
        {
            ShellPanel panel = new ShellPanel(this);
            m_frame = new JFrame("Oscar GUI Shell");
            m_frame.getContentPane().setLayout(new BorderLayout());
            m_frame.getContentPane().add(panel);
            m_frame.pack();
            m_frame.setSize(700, 400);
            m_frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            m_frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent event)
                {
                    if (m_context.getBundle().getState() == Bundle.ACTIVE)
                    {
                        try
                        {
                            m_context.getBundle().stop();
                        }
                        catch (Exception ex)
                        {
                            System.err.println("ShellGuiActivator: " + ex);
                        }
                    }
                }
            });
        }

        m_frame.setVisible(true);
    }

    private synchronized void initializePlugins()
    {
        try
        {
            // Get all model services.
            ServiceReference refs[] = m_context.getServiceReferences(
                org.apache.felix.shell.gui.Plugin.class.getName(), null);
            if (refs != null)
            {
                // Add model services to list, ignore duplicates.
                for (int i = 0; i < refs.length; i++)
                {
                    Object svcObj = m_context.getService(refs[i]);
                    if (!m_pluginList.contains(svcObj))
                    {
                        m_pluginList.add(svcObj);
                    }
                }
                firePropertyChangedEvent(
                    PLUGIN_LIST_PROPERTY, null, null);
            }
        }
        catch (Exception ex)
        {
            System.err.println("ShellGuiActivator: Error initializing model list.");
            System.err.println("ShellGuiActivator: " + ex);
            ex.printStackTrace();
        }
    }

    public void stop(BundleContext context)
    {
        if (m_frame != null)
        {
            m_frame.setVisible(false);
            m_frame.dispose();
            m_frame = null;
        }
    }

    //
    // Event methods.
    //

    public void addPropertyChangeListener(PropertyChangeListener l)
    {
        m_listenerList.add(PropertyChangeListener.class, l);
    }

    public void removeFooListener(PropertyChangeListener l)
    {
        m_listenerList.remove(PropertyChangeListener.class, l);
    }

    protected void firePropertyChangedEvent(String name, Object oldValue, Object newValue)
    {
        PropertyChangeEvent event = null;

        // Guaranteed to return a non-null array
        Object[] listeners = m_listenerList.getListenerList();

        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i = listeners.length - 2; i >= 0; i -= 2)
        {
            if (listeners[i] == PropertyChangeListener.class)
            {
                // Lazily create the event:
                if (event == null)
                {
                    event = new PropertyChangeEvent(this, name, oldValue, newValue);
                }
                ((PropertyChangeListener) listeners[i + 1]).propertyChange(event);
            }
        }
    }
}