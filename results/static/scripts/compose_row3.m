%% compose_row3.m  —  三张图排成一行（1x3），总宽 17.5 cm
%
%  用 copyobj 搬运坐标轴，保留源 .fig 内的手工改动。
%
%  三张源图（均为双栏 18.19 cm 宽设计）：
%    RTK-GUDING.fig        E/N/U 三子图
%    JINGTAISHOULIAN.fig   收敛曲线
%    RTK-DONGTAI-CDF.fig   水平误差 CDF
%
%  注意：三列各仅 5.67 cm 宽，源图内所有文字按比例缩小约 3.2 倍
%        （18.19 / 5.67），8pt 字会变成 2.5pt，需人工加大字号后才可用。

clc; close all;

%% ===================== 1. 参数 =====================
cfg.dpi  = 600;
cfg.font = 'Times New Roman';
cfg.Wcm  = 17.5;
GAP      = 0.25;                       % 列间距 (cm)

here = fileparts(mfilename('fullpath'));
if isempty(here), here = pwd; end
% 向上查找 0923配套图
srcdir = ''; p = here;
for k = 1:6
    c = fullfile(p,'0923配套图');
    if isfolder(c), srcdir = c; break; end
    p2 = fileparts(p); if strcmp(p2,p), break; end; p = p2;
end
assert(~isempty(srcdir), '找不到 0923配套图');

SRC = { 'RTK-GUDING.fig',       18.19, 13.00    % E/N/U
        'JINGTAISHOULIAN.fig',  18.19,  9.00    % 收敛
        'RTK-DONGTAI-CDF.fig',  18.19,  8.50 }; % CDF

%% ===================== 2. 版面 =====================
n    = size(SRC,1);
cw   = (cfg.Wcm - GAP*(n-1)) / n;
fprintf('每列宽 %.2f cm，间距 %.2f cm\n', cw, GAP);

scale = cw / SRC{1,2};                 % 源图都是 18.19 宽，缩放比一致
hSrc  = cell2mat(SRC(:,3));
hCol  = hSrc * scale;                  % 各列缩放后的高度
Hcm   = max(hCol);                     % 画布高取最高列
fprintf('各列高度: %.2f / %.2f / %.2f cm  -> 画布高 %.2f cm\n', ...
        hCol(1), hCol(2), hCol(3), Hcm);
fprintf('文字缩放比 %.2f 倍 (源 8pt -> %.1fpt)\n', scale, 8*scale);

%% ===================== 3. 建画布并搬运 =====================
fig = figure('Color','w','Units','inches', ...
             'Position',[1 1 cfg.Wcm/2.54 Hcm/2.54], ...
             'PaperUnits','inches','PaperSize',[cfg.Wcm/2.54 Hcm/2.54], ...
             'PaperPosition',[0 0 cfg.Wcm/2.54 Hcm/2.54], ...
             'PaperPositionMode','manual','InvertHardcopy','off');

S = struct();
for k = 1:n
    srcfile = fullfile(srcdir, SRC{k,1});
    assert(isfile(srcfile), '找不到 %s', srcfile);
    fs  = openfig(srcfile,'invisible');
    axs = flipud(findobj(fs,'Type','axes'));

    colLeft = (k-1)*(cw+GAP) / cfg.Wcm;    % 本列左边界（归一化）
    colW    = cw / cfg.Wcm;                % 本列宽（归一化）
    colH    = hCol(k) / Hcm;               % 本列高（归一化，顶部对齐）

    fprintf('  列%d: %-24s axes=%d  左=%.3f 宽=%.3f\n', ...
            k, SRC{k,1}, numel(axs), colLeft, colW);

    for i = 1:numel(axs)
        p = get(axs(i),'Position');        % 源图内归一化位置
        axNew = copyobj(axs(i), fig);
        nb = (p(2)*hCol(k)) / Hcm;         % 底部（源图纵向比例 * 本列高）
        nh = (p(4)*hCol(k)) / Hcm;
        nl = colLeft + p(1)*colW;
        nw = p(3)*colW;
        set(axNew, 'Position', [nl, nb, nw, nh]);
    end
    S.(sprintf('src%d',k)) = fs;
    S.(sprintf('ax%d',k))  = axs;
end

set(findall(fig,'-property','FontName'),'FontName',cfg.font);

%% ===================== 4. 导出 =====================
export_paper(fig, fullfile(here,'Fig_static_row3.png'), cfg);

for k=1:n
    h = S.(sprintf('src%d',k));
    if isgraphics(h), close(h); end
end
fprintf('\n图层句柄 S.ax1 / S.ax2 / S.ax3\n');
