function export_paper(f, fpath, cfg)
% EXPORT_PAPER  按锁定版面尺寸导出 PNG + 矢量 PDF
%   注意: 不能用 exportgraphics —— 它会裁掉周边空白, 导出尺寸小于版面
%         print 配合 PaperPositionMode='manual' 才严格符合期刊要求
    W = f.PaperPosition(3); H = f.PaperPosition(4);
    print(f, fpath, '-dpng', sprintf('-r%d', cfg.dpi));
    [d,b] = fileparts(fpath);
    print(f, fullfile(d,[b '.pdf']), '-dpdf', '-painters');
    info = imfinfo(fpath);
    fprintf('  导出 %-28s %.2f x %.2f cm  (%dx%d px)\n', ...
            [b '.png'], W*2.54, H*2.54, info.Width, info.Height);
end
