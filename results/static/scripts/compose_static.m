%% compose_static.m  —  静态 RTK 组图（ENU + 收敛）
%
%  把两个已调好的 .fig 拼成一张 17.5 cm 双栏图。
%  用 copyobj 搬运坐标轴 —— 保留源 .fig 里所有手工改动（如统计框只留 RMS）。
%  不重跑绘图脚本，避免覆盖手工调整。
%
%  源文件:
%    RTK-GUDING.fig       E/N/U 三子图（已扣偏移基准）
%    JINGTAISHOULIAN.fig  收敛曲线（IE PPP 未扣基准）
%
%  输出: Fig_RTK_static_combined.png / .pdf

clc; close all;

%% ===================== 1. 参数 =====================
cfg.dpi  = 600;
cfg.font = 'Times New Roman';
cfg.Wcm  = 17.5;                 % 目标总宽 (双栏)
here     = fileparts(mfilename('fullpath'));
if isempty(here), here = pwd; end

% 向上逐级查找 0923配套图 目录（它在桌面，而本脚本在 3 层子目录里）
srcdir = '';
p = here;
for k = 1:6
    cand = fullfile(p, '0923配套图');
    if isfolder(cand), srcdir = cand; break; end
    p2 = fileparts(p);
    if strcmp(p2, p), break; end
    p = p2;
end
assert(~isempty(srcdir), '向上查找 6 级仍找不到 0923配套图 目录（起始: %s）', here);
fprintf('源目录: %s\n', srcdir);

% 源图原始尺寸 (cm)，用于计算等比缩放
SRC = { 'RTK-GUDING.fig',      18.19, 13.00    % ENU
        'JINGTAISHOULIAN.fig', 18.19,  9.00 }; % 收敛
GAP = 0.30;                                    % 两图间距 (cm)

%% ===================== 2. 算版面高度 =====================
scale = cfg.Wcm / SRC{1,2};                    % 源宽相同，缩放比一致
h1 = SRC{1,3} * scale;
h2 = SRC{2,3} * scale;
Hcm = h1 + h2 + GAP;
fprintf('版面: %.2f x %.2f cm  (ENU %.2f + 收敛 %.2f + 间隙 %.2f)\n', ...
        cfg.Wcm, Hcm, h1, h2, GAP);

%% ===================== 3. 建画布并搬运 =====================
fig = figure('Color','w', 'Units','inches', ...
             'Position',[1 1 cfg.Wcm/2.54 Hcm/2.54], ...
             'PaperUnits','inches', 'PaperSize',[cfg.Wcm/2.54 Hcm/2.54], ...
             'PaperPosition',[0 0 cfg.Wcm/2.54 Hcm/2.54], ...
             'PaperPositionMode','manual', 'InvertHardcopy','off');

S = struct();
for k = 1:size(SRC,1)
    srcfile = fullfile(srcdir, SRC{k,1});
    assert(isfile(srcfile), '找不到 %s', srcfile);
    fs = openfig(srcfile, 'invisible');
    axs = flipud(findobj(fs, 'Type','axes'));   % findobj 返回倒序，翻正

    % 本块在画布中的纵向范围 (cm)：ENU 在上，收敛在下
    if k == 1
        blkBottom = h2 + GAP;   blkH = h1;
    else
        blkBottom = 0;          blkH = h2;
    end

    % 搬轴：源图归一化纵坐标 -> 按块高缩放后，加上块底偏移
    for i = 1:numel(axs)
        p = get(axs(i), 'Position');            % [left bottom width height] 归一化
        axNew = copyobj(axs(i), fig);
        newBottom = (blkBottom + p(2) * blkH) / Hcm;
        newHeight = (p(4) * blkH) / Hcm;
        set(axNew, 'Position', [p(1), newBottom, p(3), newHeight]);
    end
    S.(sprintf('src%d',k)) = fs;
    S.(sprintf('ax%d',k))  = axs;
end

set(findall(fig,'-property','FontName'), 'FontName', cfg.font);

%% ===================== 4. 导出 =====================
export_paper(fig, fullfile(here,'Fig_RTK_static_combined.png'), cfg);

% 关闭源 figure
for k = 1:size(SRC,1)
    if isgraphics(S.(sprintf('src%d',k))), close(S.(sprintf('src%d',k))); end
end

fprintf('\n图层句柄 S.ax1 (ENU 三个轴) / S.ax2 (收敛轴)\n');
