//------------- calendar.js -------------//
$(document).ready(function() {

	/* initialize the calendar
	-----------------------------------------------------------------*/
	var date = new Date();
	var d = date.getDate();
	var m = date.getMonth();
	var y = date.getFullYear();

	$('#calendar').fullCalendar({
		header: {
			left: 'prev,next today',
			center: 'title',
			right: 'month,agendaWeek,agendaDay'
		},
		buttonText: {
        	prev: '<i class="en-arrow-left8 s16"></i>',
        	next: '<i class="en-arrow-right8 s16"></i>',
        	today:'Today'
    	},
		events: [
        	{
				title: 'Seminar 1',
				start: new Date(y, m, d, 12, 0),
				end: new Date(y, m, d, 14, 0),
				allDay: false,
				description: 'Morning meeting with all staff.',
				location: 'room a',
				speaker: 'speaker 1',
				link: 'http://www.cs.ox.ac.uk'
			},
			{
				title: 'Seminar 2',
				start: new Date(y, m, d, 12, 0),
				end: new Date(y, m, d, 14, 0),
				allDay: false,
				description: 'Important backup on some servers.',
				location: 'room b',
				speaker: 'speaker 2',
				link: 'http://www.ox.ac.uk'
			},
			{
				title: 'Seminar 3',
				start: new Date(y, m, d-1, 10, 0),
				end: new Date(y, m, d-1, 12, 0),
				allDay: false,
				description: 'test',
				location: 'Room 34',
				speaker: 'speaker 3',
				link: 'http://www.ox.ac.uk'
			}
        ],

		eventClick: function(calEvent, jsEvent, view) {
			var modal = $("#infoModal");
			modal.find('.att-title').text(calEvent.title);
			modal.find('.att-starttime').text(calEvent.start);
			modal.find('.att-endtime').text(calEvent.end);
			modal.find('.att-location').text(calEvent.location);
			modal.find('.att-speaker').text(calEvent.speaker);
			modal.find('.att-abstract').text(calEvent.description);
			modal.find('.att-link').text(calEvent.link);
			modal.find('.att-link').attr('href',calEvent.link);
			modal.modal()

			// change the border color just for fun
			$(this).css('border-color', 'red');

		}

	});
	
	//force to reajust size on page load because full calendar some time not get right size.
	$(window).load(function(){
		$('#calendar').fullCalendar('render');
	});
});
