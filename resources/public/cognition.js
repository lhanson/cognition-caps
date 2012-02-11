$(document).ready(function() {
    var $thumbnails = $("#thumbnails img");
    if ($thumbnails.length) {
        var $mainImage = $("#itemImageWrapper img");
        $thumbnails
            .css("cursor", "hand").css("cursor", "pointer")
            .each(function(i, e) {
                var $img = $(e);
                $img.click(function() {
                    $mainImage.attr("src", $img.attr("src").replace("thumb", "main"));
                });
            });
    }
});

