$(document).ready(function() {
    /* Regressive enhancement to support HTML5 input elements */
    Modernizr.load({
        test: Modernizr.input.placeholder &&
              Modernizr.input.required &&
              Modernizr.inputtypes.email,
        nope: 'js/polyfill/jquery.html5form-1.5-min.js',
        complete: function() {
            if ($.fn.html5form) {
                alert('Loaded polyfill for HTML5 form validation, performing on ' + $('#emailForm'));
                $('#emailForm').html5form();
            }
        }
    });

    /* Email address submission */
    $('#emailForm').submit(function() {
        alert('Submitting form!');
        $.ajax({
            type: "POST",
            url: "bin/process.php",
            data: dataString,
            success: function() {
                alert('Success!');
                /*$('#contact_form').html("<div id='message'></div>");
                $('#message').html("<h2>Contact Form Submitted!</h2>")
                    .append("<p>We will be in touch soon.</p>")
                    .hide()
                    .fadeIn(1500, function() {
                $('#message').append("<img id='checkmark' src='images/check.png' />");*/
            });
        }
    });

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
        $.get('/sizing', function(data) {
            $('body').append(
                $('<div class="overlay" />')
                    .append($(data).find('#sizing'))
                    .height($(document).height())
            );
        });
        e.preventDefault();
    });
});

