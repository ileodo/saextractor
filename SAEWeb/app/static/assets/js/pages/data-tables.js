$(document).ready(function() {

//------------- Data tables -------------//
//basic datatables
$('#basic-datatables').dataTable({
	"oLanguage": {
	    "sSearch": "",
	    "sLengthMenu": "<span>_MENU_</span>"
	},
	"sDom": "<'row'<'col-md-6 col-xs-12 'l><'col-md-6 col-xs-12'f>r>t<'row'<'col-md-4 col-xs-12'i><'col-md-8 col-xs-12'p>>"
});

//vertical scroll
$('#vertical-scroll-datatables').dataTable( {
	"scrollY":        "200px",
	"scrollCollapse": true,
	"paging":         false
});

//responsive datatables
$('#responsive-datatables').dataTable({
	"oLanguage": {
	    "sSearch": "",
	    "sLengthMenu": "<span>_MENU_</span>"
	},
	"sDom": "<'row'<'col-md-6 col-xs-12 'l><'col-md-6 col-xs-12'f>r>t<'row'<'col-md-4 col-xs-12'i><'col-md-8 col-xs-12'p>>"
});

//with tabletools
$('#tabletools').DataTable( {
	"oLanguage": {
	    "sSearch": "",
	    "sLengthMenu": "<span>_MENU_</span>"
	},
	"sDom": "T<'row'<'col-md-6 col-xs-12 'l><'col-md-6 col-xs-12'f>r>t<'row'<'col-md-4 col-xs-12'i><'col-md-8 col-xs-12'p>>",
	tableTools: {
		"sSwfPath": "http://cdn.datatables.net/tabletools/2.2.2/swf/copy_csv_xls_pdf.swf",
		"aButtons": [ 
	      "copy", 
	      "csv", 
	      "xls",
	      "print",
	      "select_all", 
	      "select_none" 
	  ]
	}
});
 	
//------------- Sparklines -------------//
	$('#usage-sparkline').sparkline([35,46,24,56,68, 35,46,24,56,68], {
		width: '180px',
		height: '30px',
		lineColor: '#00ABA9',
		fillColor: false,
		spotColor: false,
		minSpotColor: false,
		maxSpotColor: false,
		lineWidth: 2
	});

	$('#cpu-sparkline').sparkline([22,78,43,32,55, 67,83,35,44,56], {
		width: '180px',
		height: '30px',
		lineColor: '#00ABA9',
		fillColor: false,
		spotColor: false,
		minSpotColor: false,
		maxSpotColor: false,
		lineWidth: 2
	});

	$('#ram-sparkline').sparkline([12,24,32,22,15, 17,8,23,17,14], {
		width: '180px',
		height: '30px',
		lineColor: '#00ABA9',
		fillColor: false,
		spotColor: false,
		minSpotColor: false,
		maxSpotColor: false,
		lineWidth: 2
	});

    //------------- Init pie charts -------------//
	initPieChart();

 	
});

//Setup easy pie charts
var initPieChart = function() {
	$(".pie-chart").easyPieChart({
        barColor: '#5a5e63',
        borderColor: '#5a5e63',
        trackColor: '#d9dde2',
        scaleColor: false,
        lineCap: 'butt',
        lineWidth: 10,
        size: 40,
        animate: 1500
    });
}