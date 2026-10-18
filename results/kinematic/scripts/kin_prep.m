function [cfg, T, R, Sd] = kin_prep()
% KIN_PREP  动态 RTK 绘图的公共数据准备
%   返回: cfg (配置/版面), T (真值), R (手机RTK), Sd (手机SPP)
%   三个独立绘图脚本各自调用本函数, 避免重复代码
%
%   用法:
%       [cfg, T, R, Sd] = kin_prep();

%% ---------- 配置 ----------
cfg.dpi  = 600;
cfg.font = 'Times New Roman';
cfg.tow0 = 305509.0;              % 手机首历元 (12:51:49) 对应 GPST 秒
cfg.hms0 = 12*3600 + 51*60 + 49;
cfg.badQ = [3 6];                 % 真值中剔除的坏质量码 (INS 递推/漂移)

LAYOUTS = { 'single',  8.89
            'double', 18.19 };
cfg.layout = 'double';
cfg.Wcm = LAYOUTS{find(strcmp(LAYOUTS(:,1),cfg.layout)), 2};

% 定位到本函数所在目录 (即数据目录)
cfg.outdir = fileparts(mfilename('fullpath'));
if isempty(cfg.outdir), cfg.outdir = pwd; end

%% ---------- 读数据 ----------
T  = read_truth(fullfile(cfg.outdir,'../truth_ie_ppk.csv'));
R  = read_pos(fullfile(cfg.outdir,'../solution/rtk_final.pos'), cfg);
Sd = read_pos(fullfile(cfg.outdir,'../solution/spp_l1.pos'),    cfg);
fprintf('真值 %d 历元  |  手机 RTK %d 历元  |  手机 SPP %d 历元\n', ...
        numel(T.t), numel(R.tow), numel(Sd.tow));

%% ---------- ENU 误差 ----------
la0 = T.lat(1); lo0 = T.lon(1); h0 = T.h(1);      % ENU 原点 = 真值首点

R  = add_truth(R,  T, cfg);
Sd = add_truth(Sd, T, cfg);
R  = add_enu_err(R,  la0, lo0, h0);
Sd = add_enu_err(Sd, la0, lo0, h0);
% 轨迹图需要 SPP 的绝对 ENU (add_enu_err 只算了相对真值的 dE/dN)
[Sd.E, Sd.N] = lla2enu(Sd.lat, Sd.lon, Sd.h, la0, lo0, h0);
Sd = keepvalid(Sd);
[T.E, T.N] = lla2enu(T.lat, T.lon, T.h, la0, lo0, h0);

fprintf('\n=== 动态 RTK vs 真值 (%d 历元, 固定 %d) ===\n', numel(R.dH), sum(R.Q==1));
stat_line('E  ',R.dE); stat_line('N  ',R.dN); stat_line('水平',R.dH);
fprintf('=== 动态 SPP vs 真值 (%d 历元) ===\n', numel(Sd.dH));
stat_line('E  ',Sd.dE); stat_line('N  ',Sd.dN); stat_line('水平',Sd.dH);

fprintf('\n========= 水平误差分位 (m) =========\n');
fprintf('%-8s %8s %8s %8s\n','','D50','D68','D95');
pr('RTK', R.dH);  pr('SPP', Sd.dH);

end

%% ===================== 以下为共用工具函数 =====================
function O = add_truth(O, T, cfg)
    O.rlat = nan(size(O.lat)); O.rlon = O.rlat; O.rh = O.rlat;
    for i = 1:numel(O.tow)
        k = find(abs(T.t - round(O.tow(i))) < 0.5, 1);
        if isempty(k) || any(T.Q(k)==cfg.badQ), continue; end
        O.rlat(i) = interp1(T.t, T.lat, O.tow(i), 'linear');
        O.rlon(i) = interp1(T.t, T.lon, O.tow(i), 'linear');
        O.rh(i)   = interp1(T.t, T.h,   O.tow(i), 'linear');
    end
end

function O = add_enu_err(O, la0, lo0, h0)
    [O.E, O.N, O.U] = lla2enu(O.lat, O.lon, O.h,    la0, lo0, h0);
    [Er, Nr, Ur]    = lla2enu(O.rlat, O.rlon, O.rh, la0, lo0, h0);
    O.dE = O.E-Er; O.dN = O.N-Nr; O.dU = O.U-Ur;
    O.dH = hypot(O.dE, O.dN);
    bad = ~isfinite(Er);
    O.dE(bad)=NaN; O.dN(bad)=NaN; O.dU(bad)=NaN; O.dH(bad)=NaN;
end

function O = keepvalid(O)
    f = isfinite(O.dH); fn = fieldnames(O);
    for i = 1:numel(fn)
        v = O.(fn{i});
        if isnumeric(v) && numel(v)==numel(f), O.(fn{i}) = v(f); end
    end
end

function pr(name, h)
    hs = sort(h(isfinite(h)));
    fprintf('%-8s %8.3f %8.3f %8.3f\n', name, ...
            hs(round(numel(hs)*.50)), hs(round(numel(hs)*.68)), hs(round(numel(hs)*.95)));
end

function stat_line(name, v)
    v = v(isfinite(v));
    fprintf('  %s 均值 %+7.3f  RMS %7.3f  sigma %6.3f  最大 %7.3f\n', ...
            name, mean(v), sqrt(mean(v.^2)), std(v), max(abs(v)));
end

function T = read_truth(fname)
    M = readmatrix(fname);
    T = struct('t',M(:,1),'lat',M(:,2),'lon',M(:,3),'h',M(:,4),'Q',M(:,5));
end

function S = read_pos(fname, cfg)
    lat=[];lon=[];h=[];Q=[];tow=[];
    fid = fopen(fname,'r'); assert(fid>0,'打不开 %s',fname);
    c = onCleanup(@() fclose(fid));
    while true
        ln = fgetl(fid);
        if ~ischar(ln), break; end
        if isempty(ln) || ln(1)=='%', continue; end
        p = strsplit(strtrim(ln));
        if numel(p) < 6, continue; end
        v = cellfun(@str2double, p(3:6));
        if any(isnan(v)) || abs(v(1))>90 || abs(v(2))>180, continue; end
        sec = sscanf(p{2}, '%d:%d:%f');
        if numel(sec) < 3, continue; end
        lat(end+1,1)=v(1); lon(end+1,1)=v(2); h(end+1,1)=v(3); Q(end+1,1)=v(4); %#ok<AGROW>
        tow(end+1,1) = cfg.tow0 + (sec(1)*3600+sec(2)*60+sec(3) - cfg.hms0);    %#ok<AGROW>
    end
    S = struct('lat',lat,'lon',lon,'h',h,'Q',Q,'tow',tow);
end
