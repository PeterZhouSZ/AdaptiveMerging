import xml.etree.ElementTree as ET
import xml.dom.minidom as md

from modules.box import Box
from modules.sphere import Sphere
from modules.mesh import Mesh
from modules.composite import Composite
from modules.exportXML import export

import random

root = ET.Element('root')

#animation trunk: time=12, vx=-0.75
#animation chariot: time=12, vx=-3

# Plane
plane = ET.SubElement(root, 'body')
plane.set('type','plane')
plane.set('p','0 0 0')
plane.set('n','0. 1. 0.0')
plane.set('name','plane')
fric = ET.SubElement(plane, 'friction')
fric.text = "0.001"

# Toys
y0 = 4
x0 = -22.5
z0 = -1.9

y=y0; x=x0; z=z0;
dimx=1.3; dimy=1; dimz=1;
dist=0.01;
for i in range(15):
    x+=dimx + dist;
    if (i%5==0):
        x=x0; z+=dimz + dist;
    Mesh(root, name="venus"+str(i), scale="0.23", obj="data/scaledtorso10.obj", st="data/torso_flux.sph", position=str(x)+" "+str(y0)+" "+str(z), orientation="1 0 0 -2", friction="0.6", density="0.1")

# Chariot
composite = Composite(root, obj="data/chariot.obj", scale="0.1", name="chariot", position="-20. 2.7 0.", velocity="0. 0. 0.", color="0.7 0. 0. 1.")
composite.addSphere(name='wheel1', position=" 3.15 -1.7  1.9", radius='1')
composite.addSphere(name='wheel2', position=" 3.15 -1.7 -1.9", radius='1')
composite.addSphere(name='wheel3', position="-3.15 -1.7  1.9", radius='1')
composite.addSphere(name='wheel4', position="-3.15 -1.7 -1.9", radius='1')
composite.addBox(name='panier', position="0.2 0.1 0.", dim='7.2 0.2 4')
composite.addBox(name='panierb1', position="0.2 0.75 -1.9", dim='7.2 1.5 0.2')
composite.addBox(name='panierb2', position="3.7 0.75 0.", dim='0.2 1.5 4')
composite.addBox(name='panierb3', position="0.2 0.75 1.9", dim='7.2 1.5 0.2')
composite.addBox(name='panierb4', position="-3.5 0.75 0.", dim='0.2 1.5 4')


# BookCase
composite = Composite(root, obj=None, name="bookCase", position="-40. 0. -10.", color="0.33 0.15 0.05 1.")
composite.addBox(name='wallL', position="-5 10 0", dim='0.2 20 4')
composite.addBox(name='wallR', position="5 10 0", dim='0.2 20 4')
x=-0
y=0
z=-10

colors=["0.3 0.3 0.3 1.","0.2 0.5 0.2 1.","0.3 0.3 0.5 1.","0.7 0.7 0.5 1."]
for i in range(8):
    y = str(i*2.5+2.5)
    composite.addBox(name='floor'+str(i), position=" 0. "+y+" 0.", dim='10.2 0.2 4')
    # Books
    if (i>0):
        for j in range(12):
            yb = str(float(y)-1.39)
            shift = 4.8 if (i%2==0) else 0.6
            x = str(-45+shift+0.42*j)
            orientation = "0 0 -1 0.3" if (i%2==0) else "0 0 1 0.3";
            orientation = "0 0 -1 0" if (i%3==0) else orientation;
            Box(root, name='book'+str(j), position=str(x)+" "+str(yb)+" "+str(z), dim="0.4 2 1.5", color=colors[random.randint(0,3)], orientation=orientation, friction="0.3", density="1")

# Box(root, name='bookAlone1', position="-35.75 "+yb+" 1.", dim="0.15 2 1.5", color=colors[random.randint(0,3)], friction="0.3", density="0.1")
# Box(root, name='bookAlone2', position="-35.9 "+yb+" 1.", dim="0.1 2 1.5", color=colors[random.randint(0,3)], friction="0.3", density="0.1")


export(root, "../chariotvenus.xml")
