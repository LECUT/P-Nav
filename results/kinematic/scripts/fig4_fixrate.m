%% fig4_fixrate.m  —  动态 RTK 图4: 固定率时序
%  独立脚本, 只出这一张图
%  统计每个时间窗内 Q=1(固定) 的占比, 用阶梯图呈现
%
%  数据: rtk_final.pos
%  图层:
%    L1_Ref   窗口平均固定率参考线   S.meanLine
%    L2_Rate  固定率阶梯线           S.rate
%    L3_Fix   固定解历元标记(底部)   S.marks
%    L5_Text  统计框 / 图例          S.box / S.leg

clc; close all;

[cfg, ~, R] = kin_prep();

%% ---------- 参数 ----------
WIN = 30;                    % 统计窗口 (历元, 1Hz -> 秒)

%% ---------- 按窗口统计固定率 ----------
n    = numel(R.Q);
edges = 1:WIN:(n+1);
if edges(end) < n+1, edges(end+1) = n+1; end
nw   = numel(edges) - 1;
rate = zeros(nw,1);
tmid = zeros(nw,1);
for i = 1:nw
    a = edges(i); b = edges(i+1)-1;
    rate(i) = mean(R.Q(a:b) == 1) * 100;
    tmid(i) = (a + b) / 2;
end
meanRate = mean(R.Q == 1) * 100;

fprintf('总历元 %d, 固定 %d (%.1f%%), 窗口 %d 历元\n', ...
        n, sum(R.Q==1), meanRate, WIN);
fprintf('各窗口固定率: 最小 %.1f%%  最大 %.1f%%\n', min(rate), max(rate));

%% ---------- 建图 ----------
W = cfg.Wcm; H = 8.00;
fig = figure('InvertHardcopy','off','PaperUnits','inches', ...
             'PaperSize',[W/2.54 H/2.54], 'Color',[1 1 1], ...
             'Units','inches', 'Position',[1 1 W/2.54 H/2.54], ...
             'PaperPosition',[0 0 W/2.54 H/2.54], ...
             'PaperPositionMode','manual');
ax = axes(fig); hold(ax,'on'); box(ax,'on'); grid(ax,'on');
S = struct();

% ---- L2 固定率阶梯线 ----
S.rate = stairs(ax, tmid, rate, '-', 'LineWidth',1.6, ...
                'Color',[0.00 0.45 0.74]);
S.rate.Tag = 'L2_Rate';
S.rate.DisplayName = sprintf('Fix rate (%d-s window)', WIN);

% ---- L1 平均固定率参考线 ----
S.meanLine = yline(ax, meanRate, 'r--', 'LineWidth',1.0);
S.meanLine.Tag = 'L1_Ref';
S.meanLine.UserData.role = 'mean';

% ---- L3 固定解历元标记 (贴在底部, 直观显示固定发生在何时) ----
mFix = (R.Q == 1);
S.marks = plot(ax, find(mFix), zeros(sum(mFix),1), 'v', ...
               'MarkerSize',4, 'MarkerFaceColor',[0.85 0.33 0.10], ...
               'MarkerEdgeColor','none');
S.marks.Tag = 'L3_Fix';
S.marks.DisplayName = sprintf('Fixed epoch (n=%d)', sum(mFix));

xlim(ax, [0 n]);
ylim(ax, [-6 100]);
xlabel(ax, 'Epoch (s)', 'FontSize',10);
ylabel(ax, 'Fix Rate (%)', 'FontSize',10);

% ---- L5 统计框 (左上角, 避开右上角的图例) ----
S.box = text(ax, 0.025, 0.95, ...
             sprintf('fixed %d / %d = %.1f%%\nmean %.1f%%   max %.1f%%   min %.1f%%', ...
                     sum(mFix), n, meanRate, meanRate, max(rate), min(rate)), ...
             'Units','normalized', 'HorizontalAlignment','left', ...
             'VerticalAlignment','top', 'FontSize',8, 'FontName',cfg.font, ...
             'BackgroundColor',[1 1 1], 'EdgeColor',[0.6 0.6 0.6], ...
             'LineWidth',0.5, 'Margin',2);
S.box.Tag = 'L5_Text';
S.box.Interpreter = 'none';

S.leg = legend(ax, [S.rate S.meanLine S.marks], ...
               {sprintf('Fix rate (%d-s window)', WIN), ...
                sprintf('Mean %.1f%%', meanRate), ...
                sprintf('Fixed epoch (n=%d)', sum(mFix))}, ...
               'Location','northeast', 'FontSize',9, 'Box','off', ...
               'FontName',cfg.font);
S.leg.Tag = 'L5_Text';

set(ax,'FontSize',9,'FontName',cfg.font,'TickDir','out','LineWidth',0.75);
set(findall(fig,'-property','FontName'),'FontName',cfg.font);

export_paper(fig, fullfile(cfg.outdir,'Fig_RTK_kin_fixrate.png'), cfg);

fprintf('\n图层句柄 S: %s\n', strjoin(fieldnames(S)', ', '));
