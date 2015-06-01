//------------- file.js -------------//
$(document).ready(function() {

	//------------- File upload -------------//
    $(function() {
    
        // Setup html5 version
        $("#uploader").pluploadQueue({
            // General settings
            runtimes : 'html5,flash,silverlight,html4',
            url : 'php/upload.php',
            chunk_size: '1mb',
            rename : true,
            dragdrop: true,
            
            filters : {
                // Maximum file size
                max_file_size : '10mb',
                // Specify what files to browse for
                mime_types: [
                    {title : "Image files", extensions : "jpg,gif,png"},
                    /*{title : "Zip files", extensions : "zip"}*/
                ]
            },

            // Resize images on clientside if we can
            resize : {width : 320, height : 240, quality : 90},

            flash_swf_url : '../plugins/file/plupload/Moxie.swf',
            silverlight_xap_url : '../plugins/file/plupload/Moxie.xap'
        });

    });

	//declare chart colours ( be sure are same with custom-variables.less or main.css file)
	var colours = {
		white: '#ffffff',
		dark: '#79859b',
		red : '#f68484',
		blue: '#75b9e6',
		green : '#71d398',
		yellow: '#ffcc66',
		brown: '#f78db8',
		orange : '#f4b162',
		purple : '#af91e1',
		pink : '#f78db8',
		lime : '#a8db43',
		magenta: '#eb45a7',
		teal: '#97d3c5',
		textcolor: '#5a5e63',
		gray: '#f3f5f6'
	}

	//------------- Sparklines -------------//
	$('#usage-sparkline').sparkline([35,46,24,56,68, 35,46,24,56,68], {
		width: '180px',
		height: '30px',
		lineColor: colours.dark,
		fillColor: false,
		spotColor: false,
		minSpotColor: false,
		maxSpotColor: false,
		lineWidth: 2
	});

	$('#cpu-sparkline').sparkline([22,78,43,32,55, 67,83,35,44,56], {
		width: '180px',
		height: '30px',
		lineColor: colours.dark,
		fillColor: false,
		spotColor: false,
		minSpotColor: false,
		maxSpotColor: false,
		lineWidth: 2
	});

	$('#ram-sparkline').sparkline([12,24,32,22,15, 17,8,23,17,14], {
		width: '180px',
		height: '30px',
		lineColor: colours.dark,
		fillColor: false,
		spotColor: false,
		minSpotColor: false,
		maxSpotColor: false,
		lineWidth: 2
	});

    //------------- Init pie charts -------------//
    //pass the variables to pie chart init function
    //first is line width, size for pie, animated time , and colours object for theming.
	initPieChart(10,40, 1500, colours);

 	
});

//Setup easy pie charts in sidebar
var initPieChart = function(lineWidth, size, animateTime, colours) {
	$(".pie-chart").easyPieChart({
        barColor: colours.dark,
        borderColor: colours.dark,
        trackColor: '#d9dde2',
        scaleColor: false,
        lineCap: 'butt',
        lineWidth: lineWidth,
        size: size,
        animate: animateTime
    });
}