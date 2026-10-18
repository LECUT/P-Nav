function [X,Y,Z] = llh2xyz(lat,lon,h)
% LLH2XYZ  经纬高 (deg,deg,m) -> ECEF 直角坐标 (m), WGS84
    a=6378137.0; f=1/298.257223563; e2=f*(2-f);
    lat=lat(:); lon=lon(:); h=h(:);
    N = a./sqrt(1-e2*sind(lat).^2);
    X = (N+h).*cosd(lat).*cosd(lon);
    Y = (N+h).*cosd(lat).*sind(lon);
    Z = (N*(1-e2)+h).*sind(lat);
end
