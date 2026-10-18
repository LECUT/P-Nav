function [E,N,U] = lla2enu(lat,lon,h,lat0,lon0,h0)
% LLA2ENU  LLH -> 以 (lat0,lon0,h0) 为原点的 ENU 坐标 (m)
    [X,Y,Z]    = llh2xyz(lat,lon,h);
    [X0,Y0,Z0] = llh2xyz(lat0,lon0,h0);
    dX=X-X0; dY=Y-Y0; dZ=Z-Z0;
    E = -sind(lon0).*dX + cosd(lon0).*dY;
    N = -sind(lat0).*cosd(lon0).*dX - sind(lat0).*sind(lon0).*dY + cosd(lat0).*dZ;
    U =  cosd(lat0).*cosd(lon0).*dX + cosd(lat0).*sind(lon0).*dY + sind(lat0).*dZ;
end
