%% plot_SPP_paper.m
%  SPP 静态定位误差成图 —— 参考真值 = IE (Inertial Explorer) PPP 静态解
%  数据: spp_static.pos  (RTKLIB EX 2.5, pos1-posmode=single)
%
%  图1 Fig_SPP_ENU.png  : E / N / U 三行共享 X 轴
%  图2 Fig_SPP_H_CDF.png: 水平误差时序 + 水平误差 CDF (不标注 D 分位)
%
%  风格: 沿用 论文/返修的部分图/CDF.m 与 RTKLIB_POS2ENU.m
%  尺寸: 按期刊版面锁定, 导出 PNG(600dpi) + 矢量 PDF

clc; clear; close all;

%% ===================== 1. 参数 =====================
cfg.ref.lat = 28.722085553;     % IE PPP 参考真值 (度)
cfg.ref.lon = 115.815642469;
cfg.ref.h   = 72.773;           % 椭球高 (m)

cfg.dpi  = 600;
cfg.font = 'Times New Roman';

% 版面宽度: 'single' 8.89cm | 'double' 18.19cm
LAYOUTS = { 'single',  8.89
            'double', 18.19 };
cfg.layout = 'double';
Wcm = LAYOUTS{find(strcmp(LAYOUTS(:,1),cfg.layout)), 2};

cfg.W1 = Wcm;   cfg.H1 = 11.00;    % 图1 ENU 三曲线同图
cfg.W2 = Wcm;   cfg.H2 =  8.50;    % 图2 水平误差 + CDF

cfg.outdir = fileparts(mfilename('fullpath'));
if isempty(cfg.outdir), cfg.outdir = pwd; end

%% ===================== 2. 读数据 =====================
fprintf('工作目录: %s\n', cfg.outdir);
S = read_pos_full(fullfile(cfg.outdir,'../solution/spp_static.pos'));
fprintf('SPP: %d 历元, 卫星数 %d~%d (中位 %d)\n', ...
        numel(S.lat), min(S.ns), max(S.ns), round(median(S.ns)));

%% ===================== 3. ENU =====================
[S.E, S.N, S.U] = lla2enu(S.lat, S.lon, S.h, ...
                          cfg.ref.lat, cfg.ref.lon, cfg.ref.h);
S.H = hypot(S.E, S.N);

fprintf('\n=== SPP 误差 (相对 IE-PPP) ===\n');
stat_line('E', S.E); stat_line('N', S.N); stat_line('U', S.U);
stat_line('|水平|', S.H);

tx = (0:numel(S.E)-1)';

%% ===================== 4. 图1: ENU 三条曲线同图 =====================
f1 = newpaperfig(cfg.W1, cfg.H1);
ax = axes(f1); hold(ax,'on'); box(ax,'on'); grid(ax,'on');

hE = plot(ax, tx, S.E, '-', 'Color',[0.00 0.45 0.74], 'LineWidth', 0.8);
hN = plot(ax, tx, S.N, '-', 'Color',[0.85 0.33 0.10], 'LineWidth', 0.8);
hU = plot(ax, tx, S.U, '-', 'Color',[0.47 0.67 0.19], 'LineWidth', 0.8);

yline(ax, 0, 'k:', 'LineWidth', 0.5, 'HandleVisibility','off');
xlim(ax, [0 numel(S.E)-1]);
xlabel(ax, 'Epoch (s)', 'FontSize', 10);
ylabel(ax, 'Error (m)', 'FontSize', 10);
legend(ax, [hE hN hU], {'E','N','U'}, ...
       'Location','northeast', 'FontSize',9, 'Box','off');
set(ax,'FontSize',9,'FontName',cfg.font,'TickDir','out','LineWidth',0.75);

set(findall(f1,'-property','FontName'), 'FontName', cfg.font);
export_paper(f1, fullfile(cfg.outdir,'Fig_SPP_ENU.png'), cfg);

%% ===================== 5. 图2: 水平误差 + CDF =====================
f2 = newpaperfig(cfg.W2, cfg.H2);

% ---- (a) 水平误差时序 ----
ax1 = subplot(1,2,1);
hold(ax1,'on'); box(ax1,'on'); grid(ax1,'on');
plot(ax1, tx, S.H, '-', 'Color',[0.00 0.45 0.74], 'LineWidth', 0.9);
mH = mean(S.H); rH = sqrt(mean(S.H.^2));
yline(ax1, mH, 'r--', 'LineWidth', 0.9);
yline(ax1, rH, 'k:',  'LineWidth', 0.8);
xlim(ax1, [0 numel(S.H)-1]);
xlabel(ax1, 'Epoch (s)', 'FontSize', 9);
ylabel(ax1, 'Horizontal Error (m)', 'FontSize', 9);
title(ax1, '(a) Horizontal Error', 'FontSize', 9);
set(ax1,'FontSize',8,'FontName',cfg.font,'TickDir','out','LineWidth',0.75);
add_stats_box(ax1, {sprintf('mean  %.2f m', mH), ...
                    sprintf('RMS   %.2f m', rH)}, cfg, 7);

% ---- (b) 水平误差 CDF (不标注 D 分位) ----
ax2 = subplot(1,2,2);
hold(ax2,'on'); box(ax2,'on'); grid(ax2,'on');
[fc, xc] = ecdf(S.H);
plot(ax2, xc, fc, '-', 'Color',[0.00 0.45 0.74], 'LineWidth', 1.6);
xlim(ax2, [0 max(xc)*1.05]); ylim(ax2, [0 1]);
xlabel(ax2, 'Horizontal Error (m)', 'FontSize', 9);
ylabel(ax2, 'Cumulative Probability', 'FontSize', 9);
title(ax2, '(b) CDF of Horizontal Error', 'FontSize', 9);
set(ax2,'FontSize',8,'FontName',cfg.font,'TickDir','out','LineWidth',0.75);

set(findall(f2,'-property','FontName'), 'FontName', cfg.font);
export_paper(f2, fullfile(cfg.outdir,'Fig_SPP_H_CDF.png'), cfg);

%% ===================== 分位数汇总 (仅打印, 不标在图上) =====================
fprintf('\n=========== 水平误差分位数 (m) ===========\n');
for q = [0.5 0.68 0.95 1.0]
    fprintf('  D%-4d %7.4f\n', round(q*100), quantile(S.H,q));
end
fprintf('  均值  %7.4f    RMS %7.4f\n', mean(S.H), sqrt(mean(S.H.^2)));

%% ===================== 局部函数 =====================
function add_stats_box(ax, lines, cfg, fsz)
    txt = strjoin(lines, newline);
    t = text(ax, 0.975, 0.955, txt, ...
             'Units','normalized', ...
             'HorizontalAlignment','right', ...
             'VerticalAlignment','top', ...
             'FontSize',fsz, 'FontName',cfg.font, ...
             'BackgroundColor',[1 1 1], ...
             'EdgeColor',[0.6 0.6 0.6], 'LineWidth',0.5, ...
             'Margin',2);
    t.Interpreter = 'none';
end

function stat_line(name, v)
    fprintf('  %-8s 均值 %+8.4f   RMS %7.4f   sigma %7.4f   范围 %+8.3f ~ %+8.3f\n', ...
            name, mean(v), sqrt(mean(v.^2)), std(v), min(v), max(v));
end

function f = newpaperfig(Wcm, Hcm)
    W = Wcm/2.54; H = Hcm/2.54;
    f = figure('Color','w','Units','inches','Position',[1 1 W H], ...
               'PaperUnits','inches','PaperSize',[W H], ...
               'PaperPosition',[0 0 W H],'PaperPositionMode','manual', ...
               'InvertHardcopy','off');
end

function export_paper(f, fpath, cfg)
    W = f.PaperPosition(3); H = f.PaperPosition(4);
    print(f, fpath, '-dpng', sprintf('-r%d',cfg.dpi));
    [d,b] = fileparts(fpath);
    print(f, fullfile(d,[b '.pdf']), '-dpdf', '-painters');
    info = imfinfo(fpath);
    fprintf('  导出 %-22s %.2f x %.2f cm  (%dx%d px)\n', ...
            [b '.png'], W*2.54, H*2.54, info.Width, info.Height);
end

function S = read_pos_full(fname)
    fid = fopen(fname,'r'); assert(fid>0,'打不开 %s',fname);
    c = onCleanup(@() fclose(fid));
    lat=[];lon=[];h=[];Q=[];ns=[];sdn=[];sde=[];sdu=[];
    while true
        ln = fgetl(fid);
        if ~ischar(ln), break; end
        if isempty(ln) || ln(1)=='%', continue; end
        p = strsplit(strtrim(ln));
        if numel(p) < 10, continue; end
        v = cellfun(@str2double, p(3:10));
        if any(isnan(v)), continue; end
        if abs(v(1))>90 || abs(v(2))>180, continue; end
        lat(end+1,1)=v(1); lon(end+1,1)=v(2); h(end+1,1)=v(3); %#ok<AGROW>
        Q(end+1,1)=v(4);  ns(end+1,1)=v(5);                    %#ok<AGROW>
        sdn(end+1,1)=v(6); sde(end+1,1)=v(7); sdu(end+1,1)=v(8);%#ok<AGROW>
    end
    S = struct('lat',lat,'lon',lon,'h',h,'Q',Q,'ns',ns, ...
               'sdn',sdn,'sde',sde,'sdu',sdu);
end

function [E,N,U] = lla2enu(lat,lon,h,lat0,lon0,h0)
    [X,Y,Z]    = lla2ecef(lat,lon,h);
    [X0,Y0,Z0] = lla2ecef(lat0,lon0,h0);
    dX=X-X0; dY=Y-Y0; dZ=Z-Z0;
    E = -sind(lon0).*dX + cosd(lon0).*dY;
    N = -sind(lat0).*cosd(lon0).*dX - sind(lat0).*sind(lon0).*dY + cosd(lat0).*dZ;
    U =  cosd(lat0).*cosd(lon0).*dX + cosd(lat0).*sind(lon0).*dY + sind(lat0).*dZ;
end

function [X,Y,Z] = lla2ecef(lat,lon,h)
    a=6378137.0; f=1/298.257223563; e2=f*(2-f);
    lat=lat(:); lon=lon(:); h=h(:);
    N = a./sqrt(1-e2*sind(lat).^2);
    X = (N+h).*cosd(lat).*cosd(lon);
    Y = (N+h).*cosd(lat).*sind(lon);
    Z = (N*(1-e2)+h).*sind(lat);
end
