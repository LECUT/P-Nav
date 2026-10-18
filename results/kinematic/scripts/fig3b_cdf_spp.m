%% fig3b_cdf_spp.m  —  动态 SPP 图3b: 水平误差 CDF
%  独立脚本, 只出这一张图
%
%  数据: spp_l1.pos (手机 SPP), 真值 = IE 的 BASE.txt (GNSS/INS 组合解)
%  注: 横轴范围按 SPP 自身量级设定（0~45 m），远大于 RTK 的 0~1.5 m
%
%  图层:
%    L2_CDF    全部历元 CDF        S.cdf
%    L3_CDFfix 固定解 CDF（若足够） S.cdfFix
%    L5_Text   图例                S.leg

clc; close all;

[cfg, ~, ~, Sd] = kin_prep();

W = cfg.Wcm; H = 8.50;

fig = newpaperfig(W, H);
ax = axes(fig); hold(ax,'on'); box(ax,'on'); grid(ax,'on');
S = struct();

% ---- 全部历元 ----
[fc,xc] = ecdf(Sd.dH);
S.cdf = plot(ax, xc, fc, '-', 'Color',[0.00 0.45 0.74], 'LineWidth',1.6);
S.cdf.Tag = 'L2_CDF';
S.cdf.DisplayName = sprintf('All epochs (n=%d)', numel(Sd.dH));

legItems = {S.cdf};
legText  = {sprintf('All epochs (n=%d)', numel(Sd.dH))};

% ---- 固定解单独一条（SPP 的 Q 恒为 5，无固定解，此处仅作扩展位）----
mFix = (Sd.Q == 1);
if sum(mFix) > 10
    [ff,xf] = ecdf(Sd.dH(mFix));
    S.cdfFix = plot(ax, xf, ff, '--', 'Color',[0.85 0.33 0.10], 'LineWidth',1.3);
    S.cdfFix.Tag = 'L3_CDFfix';
    S.cdfFix.DisplayName = sprintf('Fixed only (n=%d)', sum(mFix));
    legItems{end+1} = S.cdfFix;
    legText{end+1}  = sprintf('Fixed only (n=%d)', sum(mFix));
end

% 横轴按 SPP 量级（0~45 m），与 RTK 的 CDF 图刻意不同
xlim(ax,[0 45]); ylim(ax,[0 1]);
xlabel(ax,'Horizontal Error (m)','FontSize',9);
ylabel(ax,'Cumulative Probability','FontSize',9);
set(ax,'FontSize',8,'FontName',cfg.font,'TickDir','out','LineWidth',0.75);

% 注意: legend(ax, handles, labels) 的 handles 必须是句柄【数组】[h1 h2]，
%       传元胞 {h1} 会被当成 labels 解释而报错。
S.leg = legend(ax, [legItems{:}], legText, ...
               'Location','southeast','FontSize',8,'Box','off','FontName',cfg.font);
S.leg.Tag = 'L5_Text';

set(findall(fig,'-property','FontName'),'FontName',cfg.font);
export_paper(fig, fullfile(cfg.outdir,'Fig_SPP_kin_CDF.png'), cfg);

% ---- 分位汇总 ----
fprintf('\n=== 动态 SPP 水平误差分位 (m) ===\n');
h = sort(Sd.dH(isfinite(Sd.dH))); n = numel(h);
q = @(p) h(min(n, max(1, round(n*p))));
fprintf('  D50 %.3f   D68 %.3f   D95 %.3f   最大 %.3f\n', ...
        q(.5), q(.68), q(.95), max(h));

fprintf('\n图层句柄 S: %s\n', strjoin(fieldnames(S)', ', '));
