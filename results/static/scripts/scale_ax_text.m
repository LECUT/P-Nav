function scale_ax_text(ax, s, minPt)
% SCALE_AX_TEXT  按比例 s 缩放坐标轴内的文字与线宽
%
%   copyobj 只搬运图形对象，不会按目标尺寸缩放文字/线宽。
%   把一个 18.19 cm 宽的图缩到 8.6 cm 时，8pt 的字在窄框里会显得巨大，
%   挤掉绘图区。本函数把轴内所有可缩放的视觉属性统一乘以 s。
%
%   输入:
%     ax     : 坐标轴句柄（或句柄数组）
%     s      : 缩放比（目标宽 / 源宽）
%     minPt  : 字号下限（pt），防止缩到不可读。默认 5
%
%   处理对象: 轴 FontSize、标签/标题字号、图例、text、constantline 标签、
%             线宽（曲线/轴框/参考线）、marker 尺寸

    if nargin < 3 || isempty(minPt), minPt = 5; end
    for a = ax(:)'
        if ~isgraphics(a), continue; end

        % --- 轴自身 ---
        fs = get(a,'FontSize');
        set(a, 'FontSize', max(minPt, fs*s));
        try, set(a, 'LineWidth', max(0.4, get(a,'LineWidth')*s)); catch, end

        % --- 轴标签 / 标题 ---
        for tag = {'XLabel','YLabel','ZLabel','Title'}
            h = get(a, tag{1});
            if isgraphics(h)
                set(h, 'FontSize', max(minPt, get(h,'FontSize')*s));
            end
        end

        % --- 轴内所有子对象 ---
        ch = get(a,'Children');
        for i = 1:numel(ch)
            o = ch(i); if ~isgraphics(o), continue; end
            tp = get(o,'Type');
            switch tp
                case 'line'
                    try, set(o,'LineWidth', max(0.3, get(o,'LineWidth')*s)); catch, end
                    try
                        ms = get(o,'MarkerSize');
                        if ~isempty(ms) && ms > 0
                            set(o,'MarkerSize', max(2, ms*s));
                        end
                    catch, end
                case 'text'
                    set(o,'FontSize', max(minPt, get(o,'FontSize')*s));
                case 'constantline'
                    try
                        set(o,'LineWidth', max(0.3, get(o,'LineWidth')*s));
                        set(o,'FontSize', max(minPt, get(o,'FontSize')*s));
                    catch, end
                case {'scatter','bar','patch'}
                    try, set(o,'LineWidth', max(0.3, get(o,'LineWidth')*s)); catch, end
            end
        end

        % --- 图例（挂在 axes 上，不在 Children 里遍历到）---
        lg = get(a,'Legend');
        if isgraphics(lg)
            set(lg, 'FontSize', max(minPt, get(lg,'FontSize')*s));
            try, set(lg, 'LineWidth', max(0.3, get(lg,'LineWidth')*s)); catch, end
        end
    end
end
