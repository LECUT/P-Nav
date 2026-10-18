%% fig1_traj.m  —  动态轨迹对比: IE真值 + 手机RTK + 手机SPP
%  样式沿用你在 MATLAB 里调好的 GUIJI.m (自动生成代码)
%  本脚本自包含: 自己读数据、算 ENU、绘图, 直接运行即可
%
%  图层 (从底到顶, 每个图元独立句柄 + Tag):
%    L1_Ref     IE 真值轨迹线     S.ref
%    L2_Float   RTK 浮点解散点    S.float
%    L3_Fix     RTK 固定解散点    S.fix
%    L4_SPP     SPP 轨迹线        S.spp      <-- 本次新增
%    L5_Marker  起终点标记        S.start / S.end
%    L6_Text    文字标注 / 图例   S.txtStart / S.txtEnd / S.leg
%
%  调整示例:
%    S.spp.LineWidth = 2.5;
%    S.spp.Color = [0.5 0 0.5];
%    uistack(S.spp,'top');
%    set(findobj(gcf,'Tag','L2_Float'),'Visible','off');

clc; close all;

%% ===================== 1. 读完数据 =====================
[cfg, T, R, Sd] = kin_prep();

%% ===================== 2. 检查数据范围 =====================
fprintf('\nSPP 轨迹范围: E %.1f ~ %.1f m    N %.1f ~ %.1f m\n', ...
        min(Sd.E), max(Sd.E), min(Sd.N), max(Sd.N));
fprintf('RTK 轨迹范围: E %.1f ~ %.1f m    N %.1f ~ %.1f m\n', ...
        min(R.E),  max(R.E),  min(R.N),  max(R.N));

%% ===================== 3. 建图 (样式照 GUIJI.m) =====================
fig = figure('InvertHardcopy','off','PaperUnits','inches', ...
             'PaperSize',[7.16141732283465 4.7244094488189], ...
             'Color',[1 1 1], 'Units','inches', ...
             'Position',[1 1 7.16141732283465 4.7244094488189], ...
             'PaperPosition',[0 0 7.16141732283465 4.7244094488189], ...
             'PaperPositionMode','manual');

ax = axes('Parent',fig, ...
          'Position',[0.228862973760933 0.149679144385027 ...
                      0.515268422639992 0.730891265597148]);
hold(ax,'on');
S = struct();

% ---- L1 IE 真值轨迹线 (最底层) ----
S.ref = plot(ax, T.E, T.N, 'DisplayName','IE reference', 'Tag','L1_Ref', ...
             'LineWidth',2, 'Color',[0.650980392156863 0.650980392156863 0.650980392156863]);

% ---- L2 SPP 轨迹线 (次底层, 在散点之下) ----
S.spp = plot(ax, Sd.E, Sd.N, '-', 'DisplayName','SPP', 'Tag','L2_SPP', ...
             'LineWidth',1.5, 'Color',[0.494117647058824 0 0.658823529411765]);

% ---- L3 RTK 浮点解散点 ----
mFlt = (R.Q==2);
S.float = scatter(ax, R.E(mFlt), R.N(mFlt), 8, [0 0 1], ...
                  'DisplayName','RTK float', 'Tag','L3_Float', ...
                  'MarkerEdgeColor','none', 'MarkerFaceAlpha',0.3, ...
                  'MarkerFaceColor',[0 0 1]);

% ---- L4 RTK 固定解散点 (覆盖在浮点之上) ----
mFix = (R.Q==1);
S.fix = scatter(ax, R.E(mFix), R.N(mFix), 30, [1 0.411764705882353 0.16078431372549], ...
                'DisplayName','RTK fixed', 'Tag','L4_Fix', ...
                'MarkerEdgeColor','none', 'LineWidth',1, ...
                'MarkerFaceColor',[1 0.411764705882353 0.16078431372549]);

% ---- L5 起终点标记 ----
S.startMark = plot(ax, R.E(1), R.N(1), '^', 'Tag','L5_Marker', ...
                   'MarkerEdgeColor',[0 0 0], 'MarkerSize',10, ...
                   'MarkerFaceColor',[0.15 0.75 0.15], 'LineStyle','none', ...
                   'HandleVisibility','off');
S.endMark = plot(ax, R.E(end), R.N(end), 'v', 'Tag','L5_Marker', ...
                 'MarkerEdgeColor',[0 0 0], 'MarkerSize',10, ...
                 'MarkerFaceColor',[0.90 0.20 0.20], 'LineStyle','none', ...
                 'HandleVisibility','off');

% ---- L6 文字标注 ----
S.txtStart = text(ax, R.E(1), R.N(1), '  Start', 'Tag','L6_Text', ...
                  'FontName','Times New Roman', 'FontSize',9, 'Color',[0 0.45 0]);
S.txtEnd   = text(ax, R.E(end), R.N(end), '  End', 'Tag','L6_Text', ...
                  'FontName','Times New Roman', 'FontSize',9, 'Color',[0.7 0 0]);

% ---- 坐标轴 / 图例 ----
ylabel(ax,'North (m)','FontName','Times New Roman','FontSize',10);
xlabel(ax,'East (m)','FontName','Times New Roman','FontSize',10);

S.leg = legend(ax, [S.ref S.spp S.float S.fix], ...
               {'IE reference','SPP','RTK float','RTK fixed'}, ...
               'FontSize',10, 'FontName','Times New Roman');
S.leg.Tag = 'L6_Text';

box(ax,'on'); grid(ax,'on'); hold(ax,'off');
set(ax,'BoxStyle','full', 'DataAspectRatio',[1 1 1], 'FontName','Times New Roman', ...
    'FontSize',9, 'LineWidth',0.75, 'TickDir','out', ...
    'PlotBoxAspectRatio',[125 145 1]);
set(findall(fig,'-property','FontName'),'FontName','Times New Roman');

%% ===================== 4. 导出 =====================
export_paper(fig, fullfile(cfg.outdir,'Fig_RTK_kin_traj.png'), cfg);

fprintf('\n图层句柄 S: %s\n', strjoin(fieldnames(S)', ', '));
fprintf('层序 (顶->底): ');
ch = get(ax,'Children');
for i=1:numel(ch), fprintf('%s ', ch(i).Tag); end
fprintf('\n');
