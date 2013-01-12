package com.randomnoun.build.mojo.vmaint;

/* (c) 2013 randomnoun. All Rights Reserved. This work is licensed under a
 * Creative Commons Attribution 3.0 Unported License. (http://creativecommons.org/licenses/by/3.0/)
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
 * <p>The following maven properties are used:
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
 * <p>Bamboo unhelpfully lowercases labels, so the label "maven-0.0.1-SNAPSHOT" will appear as 
 * "maven-0.0.1-snapshot" .
 *
 * <p>Also, some maven build failure types (e.g. missing dependencies) will prevent the 
 * the failed build from being labelled in bamboo. There are some who would say that
 * you could put the labelling goal into a separate bamboo task to prevent this, but they 
 * would be the sort of people who think that that would be a good idea.
 *
 * @goal label-bamboo
 * @blog http://www.randomnoun.com/wp/2012/11/07/putting-a-maven-version-label-on-a-bamboo-build/
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
    	
    	// the following line would use plugin properties, rather than project properties
    	// Properties mavenProperties = getMavenSession().getExecutionProperties();
    	
    	// these can be set in a per-user settings.xml file (i.e. on the bamboo server)
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
    	String bambooPlanKey = bambooBuildKey.substring(0, bambooBuildKey.lastIndexOf("-")); 
    	
    	// The following code is the equivalent of this:
    	// /usr/bin/curl -v -X POST --user knoxg:supersecretpassword "bamboo.dev.randomnoun:8085/bamboo/rest/api/latest/result/RANDOMNOUN-NSWEB-60/label" \ 
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
