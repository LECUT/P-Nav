/*
 * 移植自 RTKLIB 2.4.3 b34（T.TAKASU 原作，C 语言实现）。
 * 原始 Java 源码经反编译工具转写，类名与结构有所调整。
 *
 *     Copyright (C) 2007-2020 by T.TAKASU, All rights reserved.
 *
 * 依 BSD 2-Clause 许可使用。许可全文见工程根目录 LICENSE-RTKLIB。
 */
package com.rtklib.bean;

import com.rtklib.bean.gtime_t;

public class sbsigp_t {
    gtime_t t0;
    short lat;
    short lon;
    short give;
    float delay;

    public sbsigp_t(gtime_t t0, short lat, short lon, short give, float delay) {
        this.t0 = t0;
        this.lat = lat;
        this.lon = lon;
        this.give = give;
        this.delay = delay;
    }

    public gtime_t getT0() {
        return this.t0;
    }

    public void setT0(gtime_t t0) {
        this.t0 = t0;
    }

    public short getLat() {
        return this.lat;
    }

    public void setLat(short lat) {
        this.lat = lat;
    }

    public short getLon() {
        return this.lon;
    }

    public void setLon(short lon) {
        this.lon = lon;
    }

    public short getGive() {
        return this.give;
    }

    public void setGive(short give) {
        this.give = give;
    }

    public float getDelay() {
        return this.delay;
    }

    public void setDelay(float delay) {
        this.delay = delay;
    }
}

