<pre>
       ____            _        _                 ____
      / ___|___  _ __ | |_ __ _(_)_ __   ___ _ __|  _ \ _ __ _____  ___   _
     | |   / _ \| '_ \| __/ _` | | '_ \ / _ \ '__| |_) | '__/ _ \ \/ / | | |
     | |__| (_) | | | | || (_| | | | | |  __/ |  |  __/| | | (_) >  <| |_| |
      \____\___/|_| |_|\__\__,_|_|_| |_|\___|_|  |_|   |_|  \___/_/\_\\__, |
                                                                       |___/

</pre>

[![Build Status](https://travis-ci.org/openanalytics/containerproxy.svg?branch=master)](https://travis-ci.org/openanalytics/containerproxy)

# ContainerProxy

ContainerProxy is an application that launches and manages containers for users, to perform specific tasks.

It is the engine that powers a.o. [ShinyProxy](https://shinyproxy.io) but can be used for any application that needs to manage HTTP proxy routes into Docker containers.

Learn more at https://containerproxy.io (in progress)

#### (c) Copyright Open Analytics NV, 2017-2023 - Apache License 2.0

## Building from source

Clone this repository and run

```
mvn -U clean install -DskipTests
```

The build will result in a single `.jar` file that is made available in the `target` directory.

## Further information

https://containerproxy.io (in progress)
