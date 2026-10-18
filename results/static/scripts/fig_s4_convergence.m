%% fig_s4_convergence.m  —  静态 RTK 收敛过程（单次实验，示意性）
%
%  【图的性质】单次观测会话的结果，用于定性说明"约需多久收敛"，
%  不作为收敛时间的统计结论。收敛时间是随机变量，严格结论需多次实验，
%  本图不承担该用途。论文图注建议注明 "single session, illustrative"。
%
%  数据: rtk_fwd.pos  (soltype = forward, 无未来信息, 等价于手机实时解算)
%        注意必须用前向解 —— 后向平滑（combined）会把文件开头也"拉平"，
%        看不到收敛过程。
%  真值: IE 的 PPP 静态解 (SDHori 0.012 m)
%
%  图层（极简设计）:
%    L1_Ref    阈值线 (2 / 1 / 0.5 m)          S.th20 / S.th10 / S.th5
%    L2_Err    水平误差曲线                    S.err
%    L3_Mark   首次达标标记 + 时刻标注         S.mk20 / ... / S.tx20 / ...

clc; close all;

%% ===================== 1. 参数 =====================
% 注意: 本图刻意不使用 stat_prep 的 subtractMean 基准, 而直接用 IE PPP 真值。
% 原因: 收敛图的主题是「实时定位多久接近真值」, 必须用外部真值作参考;
%       若扣除两机几何偏移, 等于把手机自身均值当真值, "收敛到 0.1 m"就失去意义。
cfg.dpi  = 600;
cfg.font = 'Times New Roman';
cfg.outdir = fileparts(mfilename('fullpath'));
if isempty(cfg.outdir), cfg.outdir = pwd; end
cfg.Wcm  = 18.19;                              % 源图按双栏宽设计

REF = [28.722085553, 115.815642469, 72.773];   % IE PPP 参考真值
TH  = [2 1 0.5];                               % 阈值 (m)

% 阈值线用中性灰（避免与其它图"橙=浮点 / 绿=U / 蓝=RTK"的配色撞车）
TH_GRAY = [0.35 0.35 0.35; 0.55 0.55 0.55; 0.72 0.72 0.72];

%% ===================== 2. 数据 =====================
P = read_pos_llh(fullfile(cfg.outdir,'../solution/rtk_fwd.pos'));
fprintf('前向解算: %d 历元 (%.0f 分钟)\n', numel(P.lat), numel(P.lat)/60);

[E,N,~] = lla2enu(P.lat, P.lon, P.h, REF(1), REF(2), REF(3));
H = hypot(E, N);
fprintf('水平误差: 起始 %.3f m  最小 %.3f m  末值 %.3f m\n', H(1), min(H), H(end));

tHit = nan(size(TH));
for k = 1:numel(TH)
    i = find(H < TH(k), 1);
    if ~isempty(i), tHit(k) = i-1; end
end
fprintf('\n首次达到阈值（单次结果）:\n');
for k = 1:numel(TH)
    fprintf('  < %.1f m : %.0f s\n', TH(k), tHit(k));
end

%% ===================== 3. 建图 =====================
fig = newpaperfig(cfg.Wcm, 9.00);
ax = axes(fig); hold(ax,'on'); box(ax,'on'); grid(ax,'on');
S = struct();
tx = (0:numel(H)-1)';

% ---- L1 阈值线 ----
for k = 1:numel(TH)
    nm = sprintf('th%d', round(TH(k)*10));
    S.(nm) = yline(ax, TH(k), '--', 'Color', TH_GRAY(k,:), 'LineWidth',0.9, ...
                   'Label', sprintf('%.1f m', TH(k)), ...
                   'LabelHorizontalAlignment','right', ...
                   'FontSize',8, 'FontName',cfg.font);
    S.(nm).Tag = 'L1_Ref';
    S.(nm).UserData.role = sprintf('th%.1f', TH(k));
end

% ---- L2 误差曲线（对数 Y 轴）----
S.err = semilogy(ax, tx, H, '-', 'Color',[0.00 0.45 0.74], 'LineWidth',1.5);
S.err.Tag = 'L2_Err';

% ---- L3 首次达标标记 + 时刻标注 ----
for k = 1:numel(TH)
    if isnan(tHit(k)), continue; end
    nm = sprintf('%d', round(TH(k)*10));
    S.(['mk' nm]) = plot(ax, tHit(k), TH(k), 'o', 'MarkerSize',9, ...
        'MarkerFaceColor',[0.00 0.45 0.74], 'MarkerEdgeColor','k', 'LineWidth',0.7);
    S.(['mk' nm]).Tag = 'L3_Mark';
    S.(['mk' nm]).UserData.role = sprintf('th%.1f', TH(k));
    S.(['tx' nm]) = text(ax, tHit(k)+15, TH(k)*1.30, sprintf('%.0f s', tHit(k)), ...
        'FontSize',8, 'FontName',cfg.font, 'Color',[0 0.30 0.55], 'FontWeight','bold');
    S.(['tx' nm]).Tag = 'L3_Mark';
end

set(ax, 'YScale','log');
ylim(ax, [0.15 6]);              % 未扣偏移时收敛终值约 0.30 m，下限取 0.15 m
xlim(ax, [0 numel(H)-1]);
yticks(ax, [0.2 0.5 1 2 5]);
yticklabels(ax, {'0.2','0.5','1','2','5'});
xlabel(ax, 'Epoch (s)', 'FontSize',10);
ylabel(ax, 'Horizontal Error (m)', 'FontSize',10);
set(ax,'FontSize',9,'FontName',cfg.font,'TickDir','out','LineWidth',0.75);

set(findall(fig,'-property','FontName'),'FontName',cfg.font);
export_paper(fig, fullfile(cfg.outdir,'Fig_RTK_static_convergence_new.png'), cfg);

fprintf('\n图层句柄 S: %s\n', strjoin(fieldnames(S)', ', '));
fprintf('说明: 本图为单次会话的示意结果, 不作为收敛时间的统计结论\n');
