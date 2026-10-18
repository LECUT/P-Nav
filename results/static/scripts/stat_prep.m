function [cfg, R] = stat_prep()
% STAT_PREP  静态 RTK 绘图的公共数据准备
%   返回: cfg (配置/版面), R (解算结果 + ENU 误差)
%
%   数据源: rtk_fwd.pos —— soltype = forward（前向，无未来信息）
%           静态观测的图统一用前向解：收敛过程只有前向才有意义，
%           后向平滑（combined）会把文件开头也"拉平"，看不到收敛。
%
%   参考真值: IE (Inertial Explorer) 的 PPP 静态解，SDHori 0.012 m
%
%   用法:
%       [cfg, R] = stat_prep();

%% ---------- 配置 ----------
cfg.dpi  = 600;
cfg.font = 'Times New Roman';

% 参考真值 = IE PPP（来自 0923/BASE/STATIC.txt）
cfg.ref.lat = 28.722085553;
cfg.ref.lon = 115.815642469;
cfg.ref.h   = 72.773;

% ---- 是否扣除两机几何偏移 ----
%  true  : 减去 ENU 均值 → 曲线居零，图为「重复性 / 内部一致性」
%  false : 保持 IE PPP 为基准 → 图为「绝对精度」（含两机几何偏移）
%
%  ! 注意 true 的含义：被减掉的偏移矢量（水平 0.28 m / 方位 135.5° / 垂直 1.62 m）
%  是用手机自身解算结果的均值反推的，其中同时包含
%    (a) 两台设备的真实几何距离（未知，未实测）
%    (b) 手机自身的系统性偏差（天线相位中心未标定）
%  减掉它等于假定 (b)=0，即把「手机均值」当成真值。
%  因此 true 只能支撑「重复性」结论，不能支撑「绝对精度」结论。
cfg.subtractMean = true;

LAYOUTS = { 'single',  8.89
            'double', 18.19 };
cfg.layout = 'double';
cfg.Wcm = LAYOUTS{find(strcmp(LAYOUTS(:,1),cfg.layout)), 2};

cfg.outdir = fileparts(mfilename('fullpath'));
if isempty(cfg.outdir), cfg.outdir = pwd; end

%% ---------- 读数据 ----------
R = read_pos_llh(fullfile(cfg.outdir,'../solution/rtk_fwd.pos'));
fprintf('静态 RTK (forward): %d 历元, 固定 %d (%.1f%%)\n', ...
        numel(R.lat), sum(R.Q==1), sum(R.Q==1)/numel(R.Q)*100);

%% ---------- ENU 误差（相对 IE PPP）----------
% 参考点就是 IE PPP，所以 ENU 坐标本身就是误差，命名成 dE/dN/dU 以复用绘图层
[R.dE, R.dN, R.dU] = lla2enu(R.lat, R.lon, R.h, ...
                             cfg.ref.lat, cfg.ref.lon, cfg.ref.h);

if cfg.subtractMean
    offE = mean(R.dE); offN = mean(R.dN); offU = mean(R.dU);
    R.dE = R.dE - offE;
    R.dN = R.dN - offN;
    R.dU = R.dU - offU;
    fprintf('\n[基准] 已扣除几何偏移: E %+.4f  N %+.4f  U %+.4f m', offE, offN, offU);
    fprintf('\n       水平偏移 %.4f m, 方位 %.1f°\n', ...
            hypot(offE,offN), mod(atan2d(offE,offN)+360,360));
    fprintf('       本图含义 = 重复性（相对自身均值），非绝对精度\n');
else
    fprintf('\n[基准] IE PPP 真值（含两机几何偏移），本图含义 = 绝对精度\n');
end

R.dH = hypot(R.dE, R.dN);      % 水平误差

fprintf('\n=== 静态 RTK 误差 ===\n');
stat_line('E  ', R.dE); stat_line('N  ', R.dN); stat_line('U  ', R.dU);
stat_line('水平', R.dH);

fprintf('\n========= 水平误差分位 (m) =========\n');
fprintf('%-8s %8s %8s %8s\n','','D50','D68','D95');
pr('全部', R.dH);
pr('仅固定', R.dH(R.Q==1));
pr('仅浮点', R.dH(R.Q==2));

end

%% ===================== 工具函数 =====================
function pr(name, h)
    h = h(isfinite(h));
    if numel(h) < 2, fprintf('%-8s 样本不足\n', name); return; end
    hs = sort(h);
    fprintf('%-8s %8.4f %8.4f %8.4f\n', name, ...
            hs(round(numel(hs)*.50)), hs(round(numel(hs)*.68)), hs(round(numel(hs)*.95)));
end

function stat_line(name, v)
    v = v(isfinite(v));
    fprintf('  %s 均值 %+7.4f  RMS %7.4f  sigma %6.4f  最大 %7.4f\n', ...
            name, mean(v), sqrt(mean(v.^2)), std(v), max(abs(v)));
end
