function axAll = draw_enu_panels(R, cfg, tagPrefix)
% DRAW_ENU_PANELS  画 E/N/U 三行子图 (共享 X 轴), 返回三个轴句柄
%
%  输入:
%    R         : 数据体, 需含 .dE .dN .dU
%    cfg       : 配置 (需 .font)
%    tagPrefix : Tag 前缀, 用于区分 RTK / SPP 两套图 (如 'RTK_' / 'SPP_')
%
%  每个子图内的图层:
%    L1_Ref  零线 / mean / ±sigma 参考线
%    L2/L3/L4 误差主线
%    L5_Text 统计框

comp  = { 'E', [0.00 0.45 0.74]
          'N', [0.85 0.33 0.10]
          'U', [0.47 0.67 0.19] };

fig = gcf;
axAll = gobjects(3,1);
tx = (0:numel(R.dE)-1)';

for k = 1:3
    ax = subplot(3,1,k, 'Parent',fig);
    hold(ax,'on'); box(ax,'on'); grid(ax,'on');
    axAll(k) = ax;

    v = R.(['d' comp{k,1}]);

    % ---- L1 参考线 ----
    h0 = yline(ax, 0, 'k:', 'LineWidth',0.5);
    h0.Tag = [tagPrefix 'L1_Ref'];  h0.UserData.role = 'zero';
    h0.HandleVisibility = 'off';

    vv = v(isfinite(v));
    mu = mean(vv); sd = std(vv);
    hm = yline(ax, mu, '--', 'Color',[0.35 0.35 0.35], 'LineWidth',0.8);
    hm.Tag = [tagPrefix 'L1_Ref'];  hm.UserData.role = 'mean';
    hs1 = yline(ax, mu+sd, ':', 'Color',[0.65 0.65 0.65], 'LineWidth',0.6);
    hs1.Tag = [tagPrefix 'L1_Ref']; hs1.UserData.role = 'sigma+';
    hs2 = yline(ax, mu-sd, ':', 'Color',[0.65 0.65 0.65], 'LineWidth',0.6);
    hs2.Tag = [tagPrefix 'L1_Ref']; hs2.UserData.role = 'sigma-';

    % ---- 主线 ----
    hl = plot(ax, tx, v, '-', 'Color', comp{k,2}, 'LineWidth',0.9);
    hl.Tag = [tagPrefix 'L' num2str(k+1) '_' comp{k,1}];
    hl.DisplayName = comp{k,1};

    xlim(ax, [0 numel(v)-1]);
    ylabel(ax, [comp{k,1} ' (m)'], 'FontSize',10);
    set(ax,'FontSize',9,'FontName',cfg.font,'TickDir','out','LineWidth',0.75);

    % ---- L5 统计框 ----
    tb = text(ax, 0.985, 0.94, ...
              sprintf('mean %+.2f  RMS %.2f  \\sigma %.2f', mu, sqrt(mean(vv.^2)), sd), ...
              'Units','normalized','HorizontalAlignment','right', ...
              'VerticalAlignment','top','FontSize',8,'FontName',cfg.font, ...
              'BackgroundColor',[1 1 1],'EdgeColor',[0.6 0.6 0.6], ...
              'LineWidth',0.5,'Margin',2);
    tb.Tag = [tagPrefix 'L5_Text'];
    tb.Interpreter = 'tex';

    if k < 3
        set(ax,'XTickLabel',[]);
    else
        xlabel(ax,'Epoch (s)','FontSize',10);
    end
end

linkaxes(axAll, 'x');      % X 轴联动
set(findall(fig,'-property','FontName'),'FontName',cfg.font);
end
