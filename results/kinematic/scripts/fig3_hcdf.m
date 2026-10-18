%% fig3_hcdf.m  —  动态 RTK 图3: 水平误差时序 + CDF
%  独立脚本, 只出这一张图
%
%  图层:
%    L1_Ref   参考线 (均值/ RMS)   S.mline / S.rline
%    L2_Err   水平误差时序          S.hErr
%    L2_CDF   CDF 曲线              S.cdf
%    L5_Text  统计框                S.box

clc; close all;

[cfg, ~, R] = kin_prep();

W = cfg.Wcm; H = 8.50;

fig = newpaperfig(W, H);
S = struct();
tx = (0:numel(R.dH)-1)';

% ================= (a) 水平误差时序 =================
ax1 = subplot(1,2,1); hold(ax1,'on'); box(ax1,'on'); grid(ax1,'on');

S.hErr = plot(ax1, tx, R.dH, '-', 'Color',[0.00 0.45 0.74], 'LineWidth',0.9);
S.hErr.Tag = 'L2_Err';

mH = mean(R.dH); rH = sqrt(mean(R.dH.^2));
S.mline = yline(ax1, mH, 'r--', 'LineWidth',0.9);
S.mline.Tag = 'L1_Ref';  S.mline.UserData.role = 'mean';
S.rline = yline(ax1, rH, 'k:',  'LineWidth',0.8);
S.rline.Tag = 'L1_Ref';  S.rline.UserData.role = 'rms';

xlim(ax1,[0 numel(R.dH)-1]);
xlabel(ax1,'Epoch (s)','FontSize',9);
ylabel(ax1,'Horizontal Error (m)','FontSize',9);
title(ax1,'(a) Horizontal Error','FontSize',9);
set(ax1,'FontSize',8,'FontName',cfg.font,'TickDir','out','LineWidth',0.75);

S.box = text(ax1, 0.975, 0.955, ...
             sprintf('mean  %.2f m\nRMS   %.2f m', mH, rH), ...
             'Units','normalized', 'HorizontalAlignment','right', ...
             'VerticalAlignment','top', 'FontSize',7, 'FontName',cfg.font, ...
             'BackgroundColor',[1 1 1], 'EdgeColor',[0.6 0.6 0.6], ...
             'LineWidth',0.5, 'Margin',2);
S.box.Tag = 'L5_Text';
S.box.Interpreter = 'none';

% ================= (b) CDF =================
ax2 = subplot(1,2,2); hold(ax2,'on'); box(ax2,'on'); grid(ax2,'on');

[fc,xc] = ecdf(R.dH);
S.cdf = plot(ax2, xc, fc, '-', 'Color',[0.00 0.45 0.74], 'LineWidth',1.6);
S.cdf.Tag = 'L2_CDF';

xlim(ax2,[0 max(xc)*1.05]); ylim(ax2,[0 1]);
xlabel(ax2,'Horizontal Error (m)','FontSize',9);
ylabel(ax2,'Cumulative Probability','FontSize',9);
title(ax2,'(b) CDF of Horizontal Error','FontSize',9);
set(ax2,'FontSize',8,'FontName',cfg.font,'TickDir','out','LineWidth',0.75);

set(findall(fig,'-property','FontName'),'FontName',cfg.font);
export_paper(fig, fullfile(cfg.outdir,'Fig_RTK_kin_H_CDF.png'), cfg);

fprintf('\n图层句柄 S: %s\n', strjoin(fieldnames(S)', ', '));
