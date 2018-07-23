# vlingo-wire

[![Build Status](https://travis-ci.org/vlingo/vlingo-wire.svg?branch=master)](https://travis-ci.org/vlingo/vlingo-wire) [ ![Download](https://api.bintray.com/packages/vlingo/vlingo-platform-java/vlingo-wire/images/download.svg) ](https://bintray.com/vlingo/vlingo-platform-java/vlingo-wire/_latestVersion)

The vlingo-wire toolkit provides wire protocol messaging implementations, such as with full-duplex TCP, using vlingo-actors.

### Bintray

```xml
  <repositories>
    <repository>
      <id>jcenter</id>
      <url>https://jcenter.bintray.com/</url>
    </repository>
  </repositories>
  <dependencies>
    <dependency>
      <groupId>io.vlingo</groupId>
      <artifactId>vlingo-wire</artifactId>
      <version>0.3.9</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
```

```gradle
dependencies {
    compile 'io.vlingo:vlingo-wire:0.3.9'
}

repositories {
    jcenter()
}
```

License (See LICENSE file for full license)
-------------------------------------------
Copyright © 2012-2018 Vaughn Vernon. All rights reserved.

This Source Code Form is subject to the terms of the
Mozilla Public License, v. 2.0. If a copy of the MPL
was not distributed with this file, You can obtain
one at https://mozilla.org/MPL/2.0/.

