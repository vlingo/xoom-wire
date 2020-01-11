// Copyright © 2012-2020 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.wire.node;

public enum AddressType {
  MAIN {
    public boolean isMain() {
      return true;
    }
    public String field() {
      return "addr=";
    }
  },
  OP {
    public boolean isOperational() {
      return true;
    }
    public String field() {
      return "op=";
    }
  },
  APP {
    public boolean isApplication() {
      return true;
    }
    public String field() {
      return "app=";
    }
  },
  NONE {
    public boolean isNone() {
      return true;
    }
  };

  public boolean isApplication() {
    return false;
  }

  public boolean isOperational() {
    return false;
  }

  public boolean isMain() {
    return false;
  }

  public boolean isNone() {
    return false;
  }

  public String field() {
    return "";
  }
}
