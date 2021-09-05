# xoom-wire

[![Javadocs](http://javadoc.io/badge/io.vlingo.xoom/xoom-wire.svg?color=brightgreen)](http://javadoc.io/doc/io.vlingo.xoom/xoom-wire) [![Build](https://github.com/vlingo/xoom-wire/workflows/Build/badge.svg)](https://github.com/vlingo/xoom-wire/actions?query=workflow%3ABuild) [![Download](https://img.shields.io/maven-central/v/io.vlingo.xoom/xoom-wire?label=maven)](https://search.maven.org/artifact/io.vlingo.xoom/xoom-wire) [![Gitter chat](https://badges.gitter.im/gitterHQ/gitter.png)](https://gitter.im/vlingo-platform-java/community)

The VLINGO XOOM platform SDK wire protocol messaging implementations, such as with full-duplex TCP and UDP multicast, and RSocket, using VLINGO XOOM ACTORS.

Docs: https://docs.vlingo.io/xoom-wire

### Installation

```xml
  <dependencies>
    <dependency>
      <groupId>io.vlingo.xoom</groupId>
      <artifactId>xoom-wire</artifactId>
      <version>1.8.6</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
```

```gradle
dependencies {
    compile 'io.vlingo.xoom:xoom-wire:1.8.6'
}
```

License (See LICENSE file for full license)
-------------------------------------------
Copyright © 2012-2021 VLINGO LABS. All rights reserved.

This Source Code Form is subject to the terms of the
Mozilla Public License, v. 2.0. If a copy of the MPL
was not distributed with this file, You can obtain
one at https://mozilla.org/MPL/2.0/.


### Licenses for Dependencies
SSLSocketChannel support under org.baswell.niossl under Apache 2.
Copyright 2015 Corey Baswell
Corey's suggestion is to copy his source to your project, which
we did due to Java version conflicts.

Guava is open source licensed under Apache 2 by The Guava Authors
https://github.com/google/guava/blob/0d9470c009e7ae3a8f4de8582de832dc8dffb4a4/android/guava/src/com/google/common/base/Utf8.java
