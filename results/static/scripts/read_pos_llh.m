function P = read_pos_llh(fname)
% READ_POS_LLH  读 RTKLIB .pos 文件 (llh 格式), 返回结构体
%   .lat .lon .h (deg, deg, m)   .Q (1=fix 2=float 5=single)   .ns   .sdn .sde
    lat=[];lon=[];h=[];Q=[];ns=[];sdn=[];sde=[];
    fid = fopen(fname,'r'); assert(fid>0,'打不开 %s',fname);
    c = onCleanup(@() fclose(fid));
    while true
        ln = fgetl(fid);
        if ~ischar(ln), break; end
        if isempty(ln) || ln(1)=='%', continue; end
        p = strsplit(strtrim(ln));
        if numel(p) < 10, continue; end
        v = cellfun(@str2double, p(3:10));
        if any(isnan(v)) || abs(v(1))>90 || abs(v(2))>180, continue; end
        lat(end+1,1)=v(1); lon(end+1,1)=v(2); h(end+1,1)=v(3);  %#ok<AGROW>
        Q(end+1,1)=v(4);   ns(end+1,1)=v(5);                    %#ok<AGROW>
        sdn(end+1,1)=v(6); sde(end+1,1)=v(7);                   %#ok<AGROW>
    end
    P = struct('lat',lat,'lon',lon,'h',h,'Q',Q,'ns',ns,'sdn',sdn,'sde',sde);
end
