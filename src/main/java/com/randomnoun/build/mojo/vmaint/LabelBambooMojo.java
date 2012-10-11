package com.randomnoun.build.mojo.vmaint;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.apache.maven.execution.MavenSession;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

/**
 * Maven goal which labels the current bamboo build with the version in that build's pom.xml file
 * (in the form "maven-{version}", eg "maven-0.0.1-SNAPSHOT").
 * 
 * <p>You should probably run this goal before any others, so that even failed builds are labelled
 * with the mvn version of the build.
 * 
 * <p>The following maven properties are used; these should be common across all mvn projects,
 * so you may wish to place them in your projects' parent pom.xml file.
 * 
 * <p>The 'bamboo.rest.url' maven property will be used to determine the URL of the bamboo REST API
 * <p>The 'bamboo.rest.username' and 'bamboo.rest.password' properties will be used to authenticate to the 
 * Bamboo server. The password is in cleartext. Because I enjoy sanity.
 * 
 * <p>These should be specified in the /settings/profiles/profile/properties element 
 * of your .m2/settings.xml file, but will also work if specified in your pom.xml's project properties.
 * 
 * <p>The current bamboo project and plan will be determined using the 'bambooBuildKey', 
 * 'bambooBuildNumber' and 'bambooBuildPlanName' properties, which should be set using the following
 * command-line arguments to the bamboo mvn task: 
 *
 * <pre>
  "-DbambooBuildKey=${bamboo.buildKey}" 
  "-DbambooBuildNumber=${bamboo.buildNumber}" 
  "-DbambooBuildPlanName=${bamboo.buildPlanName}"
  </pre> 
 * 
 * <p>You probably already have this in your bamboo's project's plan's stage's job's task definition, if
 * you're using a build.properties file in your project. That sentence will make sense, incidentally,
 * if you think Atlassian creates intuitive build systems.
 * 
 * <p>Speaking of which, it's entirely possible that this could be written using a Bamboo plugin 
 * rather than a maven plugin, but I've tried using the Atlassian SDK before, and I think that this 
 * will be an astonishingly more efficient way of going about this. Which is saying something, 
 * considering my general loathing of maven.
 *
 * <p>Bamboo unhelpfully lowercases labels, so the label "maven-0.0.1-SNAPSHOT" will appear as 
 * "maven-0.0.1-snapshot" .
 *
 * @goal label-bamboo
 */
public class LabelBambooMojo
    extends AbstractMojo
{
	// from http://grepcode.com/file_/repo1.maven.org/maven2/org.kuali.maven.plugins/maven-cloudfront-plugin/1.1.0/org/kuali/maven/mojo/s3/BaseMojo.java/?v=source
	
	 /**
     * The Maven project this plugin runs in.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @parameter expression="${settings}"
     * @required
     * @since 1.0
     * @readonly
     */
    private Settings settings;

    /**
     * @parameter default-value="${session}"
     * @required
     * @readonly
     */
    private MavenSession mavenSession;

    /*
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipMojo()) {
            return;
        }
        executeMojo();
    }
    */
    
    
	/**
     * @parameter expression="${label.bamboo.rest.url}"
     */
    private URL bambooRestUrl;
    
    
    /**
     * @parameter expression="${label.bamboo.rest.username}"
     */
    private String bambooRestUsername;

    /**
     * @parameter expression="${label.bamboo.rest.password}"
     */
    private String bambooRestPassword;

    
    public void execute()
        throws MojoExecutionException
    {
    	
    	// these are plugin properties, which isn't a great place for auth data
    	// Properties mavenProperties = getMavenSession().getExecutionProperties();
    	
    	// these can be set in a per-user settings.xml file (i.e. on the bamboo server)
    	// @TODO change these to bamboo.restUrl to comply with other password keys (e.g. cvs.whatever, svn.whatever)
    	Properties mavenProperties = project.getProperties();
    	String bambooRestUrlOverride = mavenProperties.getProperty("bamboo.rest.url");
    	if (bambooRestUrlOverride!=null) { 
    		try {
				bambooRestUrl = new URL(bambooRestUrlOverride);
			} catch (MalformedURLException e) {
				throw new MojoExecutionException("Invalid bamboo.rest.url property", e);
			} 
		} 
    	
    	String bambooRestUsernameOverride = mavenProperties.getProperty("bamboo.rest.username");
    	if (bambooRestUsernameOverride!=null) { 
			bambooRestUsername = bambooRestUsernameOverride;
		}

    	String bambooRestPasswordOverride = mavenProperties.getProperty("bamboo.rest.password");
    	if (bambooRestPasswordOverride!=null) { 
			bambooRestPassword = bambooRestPasswordOverride;
		}
    	
    	if (bambooRestUrl==null) {
    		throw new MojoExecutionException("Missing bamboo.rest.url property");
    	}

    	// let's just get the bamboo build data from the System properties, since
    	// that's an API that didn't take a floor full of software engineers to create.
    	String bambooBuildKey = System.getProperty("bambooBuildKey");
    	String bambooBuildNumber = System.getProperty("bambooBuildNumber");
    	//String bambooBuildPlanName = System.getProperty("bambooBuildPlanName");
    	
    	if (bambooBuildKey==null) { throw new MojoExecutionException("Missing bambooBuildKey system property"); }
    	if (bambooBuildNumber==null) { throw new MojoExecutionException("Missing bambooBuildNumber system property"); }
    	//if (bambooBuildPlanName==null) { throw new MojoExecutionException("Missing bambooBuildPlanName system property"); }
    	
    	// these days, this seem to have the values:
    	// bambooBuildKey=RANDOMNOUN-NSWEB-JOB1
    	// bambooBuildNumber=60
    	// bambooBuildPlanName=RANDOMNOUN - ns-web - Default Job
    	
        // so I'm removing the last hyphenated component from the build key to get the plan key.
    	// lets hope you can't have hyphens in job keys.
    	String bambooPlanKey = bambooBuildKey.substring(0, bambooBuildKey.lastIndexOf("-")); 
    	
    	// I'm trying to do the equivalent of this:
    	// curl -v -X POST --user knoxg:supersecretpassword "bamboo.dev.randomnoun:8085/bamboo/rest/api/latest/result/RANDOMNOUN-AFTERNOONWEB-32/label" 
    	//      -d '{ "name" : "maven-0.0.3-SNAPSHOT" }' -H "Content-type: application/json"
    	
    	try {
    		// if I was a maven developer, I'd be using wagons here. Probably. 
    		HttpClient client = new HttpClient();
    		if (bambooRestUsername!=null && bambooRestPassword!=null) {
    			getLog().debug("Authenticating with username '" + bambooRestUsername + "'");
    			getLog().debug("Authenticating with password '" + bambooRestPassword + "'");
	    		client.getState().setCredentials(
    				AuthScope.ANY,
    				new UsernamePasswordCredentials(bambooRestUsername, bambooRestPassword)
	    		);
	    		client.getParams().setAuthenticationPreemptive(true);
    		} else {
    			getLog().warn("Not authenticating to bamboo");
    		}
    		
    		String url = bambooRestUrl + "/api/latest/result/" + bambooPlanKey + "-" + bambooBuildNumber + "/label";
    		String body = "{ \"name\" : \"maven-" + project.getVersion() + "\" }";
    		getLog().info("POST " + url);
    		getLog().info("     " + body);  
    		PostMethod postMethod = new PostMethod(url);
    		postMethod.setDoAuthentication(true);
    		postMethod.setRequestHeader("Content-type", "application/json");
    		postMethod.setRequestEntity(new StringRequestEntity(body));
    		client.executeMethod(postMethod);

    		// normally returns 204 No Content, but I'm going to accept 200 as well
    		if (postMethod.getStatusCode()!=200 && postMethod.getStatusCode()!=204) {
    			throw new MojoExecutionException("Bamboo return status code " + postMethod.getStatusCode() + 
    		      ", body='" + postMethod.getResponseBodyAsString() + "'");
    		}
    	} catch (Exception e) {
    		getLog().info("Exception occurred labelling bamboo build", e);
    	}
    }
    
    /**
     * @return the project
     */
    public MavenProject getProject() {
        return project;
    }

    /**
     * @param project
     * the project to set
     */
    public void setProject(final MavenProject project) {
        this.project = project;
    }

    /**
     * @return the settings
     */
    public Settings getSettings() {
        return settings;
    }

    /**
     * @param settings
     * the settings to set
     */
    public void setSettings(final Settings settings) {
        this.settings = settings;
    }

    /**
     * @return the mavenSession
     */
    public MavenSession getMavenSession() {
        return mavenSession;
    }

    /**
     * @param mavenSession
     * the mavenSession to set
     */
    public void setMavenSession(final MavenSession mavenSession) {
        this.mavenSession = mavenSession;
    }
    
}
