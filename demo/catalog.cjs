'use strict';
// All venues, participants and engagements are fictitious. Prices reference ARS.
const general=(name,capacity)=>({name,type:'NO_NUMERADO',capacity});
const seats=(name,rows,seatsPerRow)=>({name,type:'NUMERADO',capacity:rows*seatsPerRow,rows,seatsPerRow});
const venues=[
 ['parque','Parque del Encuentro','Tandil',-37.32,-59.14,'sierras',[general('Campo general',1200),general('Campo VIP',180)]],
 ['teatro','Teatro del Molino','Tandil',-37.33,-59.13,'sierras',[seats('Platea',10,12),general('Pullman',80)]],
 ['arena','Arena Nueva Estación','Buenos Aires',-34.60,-58.43,'urbanos',[general('Campo general',1800),general('Campo VIP',300)]],
 ['club','Club Costa Abierta','Mar del Plata',-38.01,-57.55,'urbanos',[general('Popular',1000),seats('Platea',10,20)]],
 ['sala','Sala del Pasaje','La Plata',-34.92,-57.95,'urbanos',[seats('Platea',5,12),seats('Palco privado',1,2)]],
 ['estacion','Estación Encuentro','Córdoba',-31.42,-64.18,'sierras',[general('Campo general',800),general('Campo VIP',120)]],
 ['ribera','Patio de la Ribera','Rosario',-32.95,-60.64,'urbanos',[general('Campo general',900),general('Campo VIP',100)]],
 ['camara','Auditorio íntimo del Sur','Buenos Aires',-34.62,-58.40,'sierras',[seats('Platea de cámara',1,6),seats('Pullman',2,6)]]
].map(([key,name,city,latitude,longitude,organizer,sectors],i)=>({key,name,city,latitude,longitude,address:`Paseo del Encuentro ${100+i*100} (lugar ficticio)`,organizer,sectors}));
const participants=[['bruma','Bruma del Sur','Banda'],['hierro','Hierro Austral','Banda'],['pulso','Pulso Atlántico','DJ'],['abril','Abril y los Colores','Banda'],['ensamble','Ensamble Horizonte','Orquesta'],['patio','Compañía Patio Abierto','Teatro'],['risas','Colectivo Risas del Puerto','Comedia'],['cocina','Cocina de Encuentro','Gastronomía'],['codigo','Comunidad Código Sur','Disertantes'],['horizonte','Club Horizonte','Equipo'],['union','Unión del Parque','Equipo'],['litoral','Trío del Litoral','Banda']].map(([key,name,role])=>({key,name,role}));
const events=[
 ['bruma-tandil','Bruma del Sur: canciones al aire libre','Rock nacional','Recital','parque',['bruma'],'2026-12-05T20:30:00-03:00',[28000,48000],'Guitarras, canciones nuevas y un encuentro bajo el cielo de las sierras.'],
 ['hierro-cordoba','Hierro Austral: noche de metal','Metal','Recital','estacion',['hierro'],'2026-12-12T21:00:00-03:00',[32000,52000],'Riffs intensos y una puesta de luces para vivir el metal de cerca.'],
 ['pulso-ciudad','Pulso de Ciudad: edición nocturna','Electrónica','Recital','arena',['pulso'],'2026-12-19T22:00:00-03:00',[42000,60000],'Sesiones electrónicas, visuales y una pista abierta para despedir el año.'],
 ['abril-rosario','Abril y los Colores: verano pop','Pop','Recital','ribera',['abril'],'2027-01-09T20:00:00-03:00',[24000,39000],'Melodías luminosas y un show pensado para cantar en compañía.'],
 ['cuerdas-camara','Cuerdas al Atardecer','Música clásica','Recital','camara',['ensamble'],'2026-12-13T19:00:00-03:00',[12000,9000],'Música de cámara en una sala íntima, con clásicos y arreglos contemporáneos.'],
 ['patio-teatro','La casa de las pequeñas historias','Teatro','Teatro','teatro',['patio'],'2026-12-11T20:30:00-03:00',[19000,13000],'Una comedia sobre las familias que elegimos y las historias que nos reúnen.'],
 ['risas-plata','Risas de Viernes: encuentro de comedia','Stand-up','Teatro','sala',['risas'],'2026-12-18T21:00:00-03:00',[14000,18000],'Historias cotidianas, humor compartido y una noche de micrófono abierto.'],
 ['sabores-ribera','Sabores de la Ribera','Gastronomía','Festival','ribera',['cocina'],'2026-12-06T12:00:00-03:00',[14000,26000],'Cocinas regionales, charlas y degustaciones en un paseo gastronómico de verano.'],
 ['codigo-cordoba','Código Abierto: ideas que conectan','Tecnología','Conferencia','estacion',['codigo'],'2026-12-10T10:00:00-03:00',[18000,30000],'Charlas de desarrollo y experiencias de equipos para conocer nuevos proyectos.'],
 ['copa-costa','Copa Costa Abierta: final de verano','Deporte','Deporte','club',['horizonte','union'],'2027-01-17T18:00:00-03:00',[16000,25000],'Dos equipos ficticios disputan una final amistosa para disfrutar en familia.'],
 ['festival-sierras','Festival Canciones de las Sierras','Rock nacional','Festival','parque',['bruma','litoral'],'2027-01-16T18:00:00-03:00',[35000,55000],'Una tarde de bandas, puestos locales y canciones para compartir al aire libre.'],
 ['metal-plata','Hierro Austral: encuentro de guitarras','Metal','Recital','sala',['hierro'],'2027-02-06T21:00:00-03:00',[23000,34000],'Un encuentro de guitarras pesadas en formato de sala.'],
 ['pulso-rosario','Pulso en la Ribera','Electrónica','Recital','ribera',['pulso'],'2027-01-23T21:30:00-03:00',[29000,47000],'Ritmos electrónicos y una selección de la escena independiente.'],
 ['pop-capital','Colores en la Estación','Pop','Recital','arena',['abril'],'2027-01-30T20:30:00-03:00',[33000,51000],'Una fiesta de pop con canciones nuevas, coros y luces de verano.'],
 ['clasica-tandil','Horizonte Sinfónico: viaje de cuerdas','Música clásica','Recital','teatro',['ensamble'],'2027-02-07T19:30:00-03:00',[17000,11000],'Pequeñas historias que acercan la música de concierto.'],
 ['teatro-plata','El patio de los encuentros','Teatro','Teatro','sala',['patio'],'2027-01-22T20:00:00-03:00',[15000,22000],'Una obra sobre la amistad y las segundas oportunidades.'],
 ['sabores-costa','Mesa Abierta: sabores del verano','Gastronomía','Festival','club',['cocina'],'2027-01-10T12:30:00-03:00',[15000,28000],'Un encuentro de cocinas y productores ficticios para todas las edades.'],
 ['ideas-capital','Ideas del Sur: encuentro tecnológico','Tecnología','Conferencia','arena',['codigo'],'2027-02-13T09:30:00-03:00',[20000,36000],'Experiencias de producto, software y comunidades en una jornada compartida.']
].map(([key,name,category,eventType,venue,artists,startDateTime,prices,description])=>({key,name,category,eventType,venue,participants:artists,startDateTime,prices,description:description+' Evento y participantes ficticios, exclusivamente para demostración.'}));
const users=[{key:'admin',role:'ADMINISTRADOR',email:'administracion@demo.ticketya.test'},{key:'sierras',role:'ORGANIZADOR',email:'produccion.sierras@demo.ticketya.test'},{key:'urbanos',role:'ORGANIZADOR',email:'escenarios.urbanos@demo.ticketya.test'},{key:'acceso',role:'PERSONAL_ACCESO',email:'accesos@demo.ticketya.test'},...['ana','bruno','carla'].map(key=>({key,role:'CLIENTE',email:key+'@demo.ticketya.test'}))];
module.exports={fictitious:true,priceReference:'ARS; el dominio persiste unidades monetarias sin código ISO',users,venues,participants,events,scenarios:[
 {key:'palco-completo',event:'risas-plata',sector:1,quantity:2,client:'ana',outcome:'approved'},
 {key:'ultimas-plateas',event:'cuerdas-camara',sector:0,quantity:5,client:'bruno',outcome:'approved'},
 {key:'devolucion-parcial',event:'sabores-ribera',sector:0,quantity:2,client:'carla',outcome:'partial-refund'},
 {key:'tarjeta-rechazada',event:'pulso-ciudad',sector:1,quantity:2,client:'bruno',outcome:'rejected'},
 {key:'reserva-temporal',event:'bruma-tandil',sector:0,quantity:2,client:'ana',outcome:'reservation'}]};
