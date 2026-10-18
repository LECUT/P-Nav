%% fig2b_enu_spp.m  —  动态 SPP 图2b: E / N / U 误差时序 (上中下三个子图)
%  独立脚本, 只出这一张图。绘图逻辑在 draw_enu_panels.m (与 RTK 版共用)
%
%  数据: spp_l1.pos (手机 SPP), 真值 = IE 的 BASE.txt
%  注: SPP 只有 546 个历元参与统计 (已剔除真值 Q=3/6 的坏历元)
%
%  图层 (每个子图内):
%    L1_Ref  零线 / mean / ±sigma  参考线
%    L2_E / L3_N / L4_U   误差主线
%    L5_Text 统计框

clc; close all;

[cfg, ~, ~, Sd] = kin_prep();

W = cfg.Wcm; H = 13.00;
fig = figure('InvertHardcopy','off','PaperUnits','inches', ...
             'PaperSize',[W/2.54 H/2.54], 'Color',[1 1 1], ...
             'Units','inches', 'Position',[1 1 W/2.54 H/2.54], ...
             'PaperPosition',[0 0 W/2.54 H/2.54], ...
             'PaperPositionMode','manual');

axAll = draw_enu_panels(Sd, cfg, 'SPP_');

S.E  = findobj(fig,'Tag','SPP_L2_E');
S.N  = findobj(fig,'Tag','SPP_L3_N');
S.U  = findobj(fig,'Tag','SPP_L4_U');

export_paper(fig, fullfile(cfg.outdir,'Fig_SPP_kin_ENU.png'), cfg);

fprintf('\n=== 动态 SPP 各分量 (对真值) ===\n');
for nm = {'E','N','U'}
    v = Sd.(['d' nm{1}]); v = v(isfinite(v));
    fprintf('  %s  mean %+7.3f  RMS %7.3f  sigma %6.3f  max %7.3f\n', ...
            nm{1}, mean(v), sqrt(mean(v.^2)), std(v), max(abs(v)));
end
fprintf('\n轴句柄 axAll = [axE axN axU];  主线 S.E / S.N / S.U\n');
