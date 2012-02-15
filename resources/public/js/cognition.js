$(document).ready(function() {
    /* Thumbnail toggling for item pages */
    var $thumbnails = $('#thumbnails img');
    if ($thumbnails.length) {
        var $mainImage = $('#itemImageWrapper img');
        $thumbnails
            .css('cursor', 'pointer')
            .each(function(i, e) {
                var $img = $(e);
                if (i === 0) {
                    $img.addClass('selected');
                }
                $img.click(function() {
                    $thumbnails.removeClass('selected');
                    $img.addClass('selected');
                    $mainImage.attr('src', $img.attr('src').replace('thumb', 'main'));
                });
            });
    }

    /* Overlay for sizing chart */
    $('#sizingLink').click(function(e) {
        var $overlay = $('#overlay');
        if ($overlay.length) {
            $overlay.show();
        }
        else {
            $overlay = $('<div id="overlay" />').height($(document).height());
            if (!Modernizr.rgba) {
                $overlay.css('background-image', 'url("/images/overlay_bg.png")');
            }
            $('body').append($overlay);
            var $overlayClose =
                $('<a id="overlayClose" title="Close"></a>')
                    .click(function() { $overlay.hide(); });
            $.get('/sizing', function(data) {
                var $content = $(data).find('#sizing').append($overlayClose);
                $overlay.append($content);
            });
        }
        e.preventDefault();
    });
});

