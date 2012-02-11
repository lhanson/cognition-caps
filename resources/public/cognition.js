$(document).ready(function() {
    /* Thumbnail toggling for item pages */
    var $thumbnails = $("#thumbnails img");
    if ($thumbnails.length) {
        var $mainImage = $("#itemImageWrapper img");
        $thumbnails
            .css("cursor", "hand").css("cursor", "pointer")
            .each(function(i, e) {
                var $img = $(e);
                if (i === 0) {
                    $img.addClass("selected");
                }
                $img.click(function() {
                    $thumbnails.removeClass("selected");
                    $img.addClass("selected");
                    $mainImage.attr("src", $img.attr("src").replace("thumb", "main"));
                });
            });
    }
});

