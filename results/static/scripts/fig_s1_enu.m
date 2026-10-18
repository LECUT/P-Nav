%% fig_s1_enu.m  —  静态 RTK 图1: E / N / U 误差时序 (上中下三个子图)
%  独立脚本, 只出这一张图。绘图逻辑在 draw_enu_panels.m (与动态版共用)
%
%  数据: rtk_fwd.pos (soltype = forward, 静态)
%  真值: IE 的 PPP 静态解 (SDHori 0.012 m)
%
%  图层 (每个子图内):
%    L1_Ref  零线 / mean / ±sigma  参考线
%    L2_E / L3_N / L4_U   误差主线
%    L5_Text 统计框

clc; close all;

[cfg, R] = stat_prep();

fig = newpaperfig(cfg.Wcm, 13.00);
axAll = draw_enu_panels(R, cfg, 'STA_');

S.E = findobj(fig,'Tag','STA_L2_E');
S.N = findobj(fig,'Tag','STA_L3_N');
S.U = findobj(fig,'Tag','STA_L4_U');

export_paper(fig, fullfile(cfg.outdir,'Fig_RTK_static_ENU.png'), cfg);

fprintf('\n轴句柄 axAll = [axE axN axU];  主线 S.E / S.N / S.U\n');
