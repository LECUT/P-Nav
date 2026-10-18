/*
 * Derived from the open-source myGNSS application.
 *
 *     Copyright (c) 2024, z1Yang-1, All rights reserved.
 *     Licensed under the BSD 2-Clause License.
 *     https://github.com/z1Yang-1/myGNSS
 *
 * Substantially modified and extended for P-Nav.
 * See THIRD-PARTY-NOTICES.md for details.
 */
package com.ecut.pnav.ui.Ntrip;

import java.io.Serializable;

public class NtripClient implements Serializable
{
    private static final long serialVersionUID = 1L; // 推荐定义的版本号
    private String host;
    private int port;
    private String MountPoint;
    private String Ntrip_User;
    private String Ntrip_password;
    public  NtripClient(){}
    public NtripClient(String host, int port, String MountPoint, String user, String password){
        this.host = host;
        this.port = port;
        this.MountPoint = MountPoint;
        this.Ntrip_User = user;
        this.Ntrip_password = password;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getMountPoint() {
        return MountPoint;
    }

    public String getNtrip_User() {
        return Ntrip_User;
    }

    public String getNtrip_password() {
        return Ntrip_password;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setMountPoint(String mountPoint) {
        MountPoint = mountPoint;
    }

    public void setNtrip_User(String ntrip_User) {
        Ntrip_User = ntrip_User;
    }

    public void setNtrip_password(String ntrip_password) {
        Ntrip_password = ntrip_password;
    }
}
