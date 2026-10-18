%% compose_A.m  —  静态图组版（方案甲：ENU 独占一行，收敛 + CDF 并排第二行）
%
%  布局:
%    +---------------------------------------+
%    |            ENU (E/N/U 三子图)         |   17.5 x 12.5 cm
%    +---------------------------------------+
%    |      收敛        |       CDF          |   17.5 x  5.5 cm
%    +---------------------------------------+
%    总高约 18.3 cm
%
%  源 .fig（均按双栏 18.19 cm 设计）:
%    RTK-GUDING.fig        E/N/U 三子图（已扣偏移基准）
%    JINGTAISHOULIAN.fig   收敛曲线（IE PPP 未扣基准）
%    RTK-DONGTAI-CDF.fig   水平误差 CDF
%
%  关键: copyobj 不缩放文字/线宽，故搬运后调用 scale_ax_text 按目标宽度等比缩放，
%        否则窄框里的文字会挤掉绘图区。

clc; close all;

%% ===================== 1. 参数 =====================
cfg.dpi  = 600;
cfg.font = 'Times New Roman';
cfg.Wcm  = 17.5;          % 总宽
GAP      = 0.35;          % 上下/左右间距 (cm)
MINPT    = 5;             % 字号下限

here = fileparts(mfilename('fullpath'));
if isempty(here), here = pwd; end
srcdir = ''; p = here;
for k = 1:6
    c = fullfile(p,'0923配套图');
    if isfolder(c), srcdir = c; break; end
    p2 = fileparts(p); if strcmp(p2,p), break; end; p = p2;
end
assert(~isempty(srcdir), '找不到 0923配套图');

SRCW = 18.19;             % 源图统一宽度

%% ===================== 2. 各部件的目标尺寸 =====================
% 关键: 源 .fig 里轴只占源画布的一部分（轴 Position 不是 [0 0 1 1]），
%       若按"整块等比缩放"搬运，源图的留白也会被搬过来，且各源的留白不同 -> 对不齐。
%       因此改为「按绘图区对齐」：算出源轴的归一化范围，映射到目标块时
%       让它铺满（保留少量边距供轴标签使用）。
EDGE_L = 0.13;   % 目标块左侧留给 Y 轴标签的比例
EDGE_R = 0.02;
EDGE_B = 0.16;   % 底部留给 X 轴标签
EDGE_T = 0.04;

% 上排：ENU 独占全宽
enuW = cfg.Wcm;    enuBlockH = 12.0;
% 下排：收敛 + CDF 各半
halfW  = (cfg.Wcm - GAP) / 2;
botBlockH = 5.6;

Hcm = enuBlockH + GAP + botBlockH;
fprintf('版面 %.2f x %.2f cm  (上 %.2f + 间隙 %.2f + 下 %.2f)\n', ...
        cfg.Wcm, Hcm, enuBlockH, GAP, botBlockH);

%% ===================== 3. 建画布 =====================
fig = figure('Color','w','Units','inches', ...
             'Position',[1 1 cfg.Wcm/2.54 Hcm/2.54], ...
             'PaperUnits','inches','PaperSize',[cfg.Wcm/2.54 Hcm/2.54], ...
             'PaperPosition',[0 0 cfg.Wcm/2.54 Hcm/2.54], ...
             'PaperPositionMode','manual','InvertHardcopy','off');

% 部件表: {文件名, 目标块左下Xcm, 目标块左下Ycm, 目标块宽cm, 目标块高cm}
PARTS = { 'RTK-GUDING.fig',      0,          botBlockH+GAP, enuW,  enuBlockH
          'JINGTAISHOULIAN.fig', 0,          0,             halfW, botBlockH
          'RTK-DONGTAI-CDF.fig', halfW+GAP,  0,             halfW, botBlockH };

S = struct();
for k = 1:size(PARTS,1)
    fname = PARTS{k,1};
    tX = PARTS{k,2};  tY = PARTS{k,3};
    tW = PARTS{k,4};  tH = PARTS{k,5};
    s  = tW / SRCW;                            % 字号缩放比（按宽度）

    srcfile = fullfile(srcdir, fname);
    assert(isfile(srcfile), '找不到 %s', srcfile);
    fs  = openfig(srcfile,'invisible');
    axs = flipud(findobj(fs,'Type','axes'));

    % 求所有源轴在源图内的包围盒（归一化）
    bb = [inf inf -inf -inf];                  % [l b r t]
    for i = 1:numel(axs)
        p = get(axs(i),'Position');
        bb(1) = min(bb(1), p(1));
        bb(2) = min(bb(2), p(2));
        bb(3) = max(bb(3), p(1)+p(3));
        bb(4) = max(bb(4), p(2)+p(4));
    end
    fprintf('  %-22s axes=%d  源轴包围盒 [%.3f %.3f %.3f %.3f]  缩放 %.3f\n', ...
            fname, numel(axs), bb, s);

    % 源包围盒 -> 目标可用绘图区
    availW = tW * (1 - EDGE_L - EDGE_R);
    availH = tH * (1 - EDGE_B - EDGE_T);
    % 源包围盒的宽高（归一化）
    srcBW = bb(3)-bb(1);
    srcBH = bb(4)-bb(2);

    for i = 1:numel(axs)
        p = get(axs(i),'Position');
        axNew = copyobj(axs(i), fig);

        % 源轴相对包围盒的位置（0..1），再映射到目标可用区
        relL = (p(1)-bb(1)) / srcBW;
        relB = (p(2)-bb(2)) / srcBH;
        relW = p(3) / srcBW;
        relH = p(4) / srcBH;

        xCm = tX + tW*EDGE_L + relL*availW;
        yCm = tY + tH*EDGE_B + relB*availH;
        wCm = relW*availW;
        hCm = relH*availH;

        set(axNew, 'Position', [xCm/cfg.Wcm, yCm/Hcm, wCm/cfg.Wcm, hCm/Hcm]);

        % 缩放轴内文字与线宽（copyobj 不做这件事）
        scale_ax_text(axNew, s, MINPT);
    end
    S.(sprintf('src%d',k)) = fs;
    S.(sprintf('ax%d',k))  = axs;
end

set(findall(fig,'-property','FontName'),'FontName',cfg.font);

%% ===================== 4. 导出 =====================
export_paper(fig, fullfile(here,'Fig_static_A.png'), cfg);

for k=1:size(PARTS,1)
    h = S.(sprintf('src%d',k));
    if isgraphics(h), close(h); end
end
fprintf('\n图层句柄 S.ax1=ENU(3轴) / S.ax2=收敛 / S.ax3=CDF\n');
