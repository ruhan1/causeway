/**
 * Copyright (C) 2015 Red Hat, Inc. (jbrazdil@redhat.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.causeway.test;

import org.apache.commons.io.FileUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.commonjava.util.jhttpc.HttpFactory;
import org.commonjava.util.jhttpc.auth.MemoryPasswordManager;
import org.commonjava.util.jhttpc.auth.PasswordManager;
import org.commonjava.util.jhttpc.model.SiteConfig;
import org.commonjava.util.jhttpc.model.SiteConfigBuilder;
import org.commonjava.util.jhttpc.util.UrlUtils;
import org.jboss.pnc.causeway.test.spi.CausewayDriver;
import org.jboss.pnc.causeway.test.util.HttpCommands;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.MalformedURLException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * Created by jdcasey on 2/11/16.
 */
public class LocalTestDriver
        implements CausewayDriver
{

    private PasswordManager passwordManager;

    private HttpFactory httpFactory;

    private SiteConfig siteConfig;

    private TemporaryFolder temp = new TemporaryFolder();

    private File configDir;

    @Override
    public void start()
            throws Exception
    {
        temp.create();

        configDir = temp.newFolder( "local-causeway-etc" );

        File mainConf = new File( configDir, "etc/main.conf" );
        mainConf.getParentFile().mkdirs();

        FileUtils.write( mainConf, "koji.url=https://koji.myco.com/kojihub\n"
                + "pncl.url=https://pncl.myco.com/\n"
                + "koji.client.pem.password = mypassword" );

        System.out.println(
                "Wrote configuration: " + mainConf + " with configuration:\n\n" + FileUtils.readFileToString(
                        mainConf ) );


        //booter = new Booter();
        //bootStatus = booter.start(  );

        /*if ( bootStatus == null )
        {
            fail( "No boot status" );
        }*/

        //Throwable t = bootStatus.getError();
        /*if ( t != null )
        {
            throw new RuntimeException( "Failed to start Causeway test server.", t );
        }*/

        //assertThat( bootStatus.isSuccess(), equalTo( true ) );

        passwordManager = new MemoryPasswordManager();
        siteConfig = new SiteConfigBuilder( "local-test", formatUrl().toString() ).build();
        httpFactory = new HttpFactory( passwordManager );
    }

    @Override
    public void stop()
            throws Exception
    {
        temp.delete();
    }

    private void checkStarted()
    {
    }

    @Override
    public int getPort()
    {
        checkStarted();
        return 8080;
    }

    @Override
    public String formatUrl( String... pathParts )
            throws MalformedURLException
    {
        checkStarted();
        return UrlUtils.buildUrl( String.format( "http://localhost:%d", getPort() ), pathParts );
    }

    @Override
    public HttpFactory getHttpFactory()
            throws Exception
    {
        return httpFactory;
    }

    @Override
    public SiteConfig getSiteConfig()
            throws Exception
    {
        return siteConfig;
    }

    @Override
    public PasswordManager getPasswordManager()
            throws Exception
    {
        return passwordManager;
    }

    @Override
    public void withNewHttpClient( HttpCommands commands )
            throws Exception
    {
        try (CloseableHttpClient client = httpFactory.createClient( siteConfig ))
        {
            commands.execute( this, client ).throwError();
        }
    }
}
