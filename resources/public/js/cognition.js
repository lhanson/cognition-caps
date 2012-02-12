$(document).ready(function() {
    /* Regressive enhancement to support HTML5 input elements */
    /*Modernizr.load({
        test: Modernizr.input.placeholder &&
              Modernizr.input.required &&
              Modernizr.inputtypes.email,
        nope: 'js/polyfill/jquery.html5form-1.5-min.js',
        complete: function() {
            if ($.fn.html5form) {
                alert('Loaded polyfill for HTML5 form validation, performing on ' + $('#emailForm'));
                $('#emailForm')
                    .after($('<div id="formValidation" />'))
                    .html5form({
                        messages: 'en',
                        responseDiv: '#formValidation'
                    });
            }
        }
    });*/

    /* Thumbnail toggling for item pages */
    var $thumbnails = $('#thumbnails img');
    if ($thumbnails.length) {
        var $mainImage = $('#itemImageWrapper img');
        $thumbnails
            .css('cursor', 'hand').css('cursor', 'pointer')
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
            var $overlayClose =
                $('<a id="overlayClose" title="Close"></a>')
                    .click(function() { $overlay.hide(); });
            $.get('/sizing', function(data) {
                var $content = $(data).find('#sizing').append($overlayClose);
                $('body').append($overlay.append($content));
            });
        }
        e.preventDefault();
    });
});

