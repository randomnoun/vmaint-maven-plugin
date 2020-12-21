# vmaint-maven-plugin

**vmaint-maven-plugin** is a maven plugin that applies a label to a bamboo plan during a bamboo build.

This makes it slightly easier to see which version you were building / deploying when you go back through the build logs after something breaks.

## How does it work ?

In your build job, change the command-line being passed to mvn to include the **vmaint:label-bamboo** goal.

You'll also need to include a few extra maven variables. 
Here's an example of what to put in the 'goal' textbox in the bamboo job: 
```
-B  vmaint:label-bamboo clean deploy 
"-DbambooBuildKey=${bamboo.buildKey}" 
"-DbambooBuildNumber=${bamboo.buildNumber}" 
"-DbambooBuildPlanName=${bamboo.buildPlanName}"
```

## How does it authenticate ?

You'll also need to update your maven settings.xml on the bamboo server with some values used to authenticate to the Bamboo API:
```
<settings>

  <pluginGroups>
    <pluginGroup>com.randomnoun.maven.plugins</pluginGroup>
  </pluginGroups>

  <profiles>
    <profile>
      <id>default</id>
      
      <properties>
        <bamboo.rest.url>http://my-bamboo-server/bamboo/rest</bamboo.rest.url>  
        <bamboo.rest.username>my-bamboo-username</bamboo.rest.username>  
        <bamboo.rest.password>my-bamboo-password</bamboo.rest.password>  
      </properties>
    </profile>
  </profiles>
  
  <activeProfiles>
    <activeProfile>default</activeProfile>
  </activeProfiles>
</settings>
```

You don't strictly need the pluginGroup section, but that allows you to run the goal `vmaint:label-bamboo` instead of `com.randomnoun.maven.plugins:vmaint-maven-plugin:label-bamboo` which is a bit snappier.

## Anything else ?

I wrote a blog post about this at http://www.randomnoun.com/wp/2012/11/07/putting-a-maven-version-label-on-a-bamboo-build/ . 

It has screenshots. 

From 2012.


## Licensing

vmaint-maven-plugin is licensed under the BSD 2-clause license.