// rain — tools/site/agsl2glsl.mjs 가 app/src/main/assets/scenes/_lib.agsl + rain.agsl 에서 만든 파일. 손으로 고치지 말 것.
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform vec2 uSize;
uniform float uTime;
uniform float uFlow;
uniform float uDay;
uniform float uWind;
uniform float uRain;
uniform float uLevel;
uniform float uSoft;
uniform float uSeed;
uniform float uGrade;
float sat(float x){return clamp(x,0.0,1.0);}
float sq(float x){return x*x;}
vec3 hx(float r,float g,float b){return vec3(r,g,b)/255.0;}
float h11(float n){return fract(sin(n*127.1+311.7)*43758.5453);}
float h21(vec2 p){
p=fract(p*vec2(123.34,456.21));
p+=dot(p,p+45.32);
return fract(p.x*p.y);
}
vec2 h22(vec2 p){float n=h21(p);return vec2(n,h21(p+n*31.7+17.17));}
float vn(vec2 p){
vec2 i=floor(p);
vec2 f=fract(p);
vec2 u=f*f*(3.0-2.0*f);
float a=h21(i);
float b=h21(i+vec2(1.0,0.0));
float c=h21(i+vec2(0.0,1.0));
float d=h21(i+vec2(1.0,1.0));
return mix(mix(a,b,u.x),mix(c,d,u.x),u.y);
}
float fbm(vec2 p){
float v=0.0;
float a=0.5;
for(int i=0;i<5;i++){v+=a*vn(p);p=p*2.03+vec2(13.7,7.1);a*=0.5;}
return v;
}
float fbm3(vec2 p){
float v=0.0;
float a=0.5;
for(int i=0;i<3;i++){v+=a*vn(p);p=p*2.03+vec2(13.7,7.1);a*=0.5;}
return v/0.875;
}
mat2 rot(float a){float c=cos(a);float s=sin(a);return mat2(c,-s,s,c);}
float dayK(){float t=mod(uDay,4.0);float f=fract(t);return floor(t)+f*f*(3.0-2.0*f);}
vec3 day4(vec3 a,vec3 b,vec3 c,vec3 d){
float t=dayK();
if(t<1.0)return mix(a,b,t);
if(t<2.0)return mix(b,c,t-1.0);
if(t<3.0)return mix(c,d,t-2.0);
return mix(d,a,t-3.0);
}
float day4f(float a,float b,float c,float d){
float t=dayK();
if(t<1.0)return mix(a,b,t);
if(t<2.0)return mix(b,c,t-1.0);
if(t<3.0)return mix(c,d,t-2.0);
return mix(d,a,t-3.0);
}
float gust(float x,float t){
float g=vn(vec2(x*0.9-t*0.35,t*0.07))*0.65+vn(vec2(x*2.3-t*0.8,3.7))*0.35;
return sat(g*(0.55+0.9*uWind));
}
float stars(vec2 p,float k){
vec2 g=p*k;
vec2 id=floor(g);
vec2 f=fract(g)-0.5;
float r=h21(id+3.1);
if(r<0.80)return 0.0;
vec2 o=(h22(id+9.7)-0.5)*0.7;
float d=length(f-o);
float tw=0.55+0.45*sin(uTime*(0.6+2.2*h21(id+1.3))+r*40.0);
float s=(r-0.80)*5.0;
return smoothstep(0.09*(0.4+s),0.0,d)*tw*(0.35+0.65*s);
}
float glow(float d,float r){return exp(-d*d/(r*r));}
vec4 finish(vec3 col,vec2 xy){
vec2 uv=xy/uSize;
float l=dot(col,vec3(0.299,0.587,0.114));
if(uGrade>1.5){
float k=smoothstep(0.02,0.92,l);
vec3 ink=mix(vec3(0.085,0.078,0.072),vec3(0.80,0.77,0.71),k*k*(1.6-0.6*k));
col=mix(ink,col,0.10);
}else if(uGrade>0.5){
col=mix(col,vec3(l),0.10);
col=col*vec3(1.03,1.0,0.95)+vec3(0.028,0.022,0.034);
}
float g=uGrade>0.5?0.060:0.020;
float n=h21(xy+fract(uTime*0.37)*91.0)-0.5;
col+=vec3(n*g);
vec2 v=uv-0.5;
col*=1.0-0.26*dot(v,v);
return vec4(clamp(col,vec3(0.0),vec3(1.0)),1.0);
}
const float HZ=0.70;
const float TAU=6.2831853;
const float ACC=0.12;
float invSmooth(float y){return 0.5-sin(asin(1.0-2.0*clamp(y,0.0,1.0))/3.0);}
float wig(float y,float ph){
return 0.070*sin(y*2.9+ph)+0.030*sin(y*9.0+ph*1.7+1.2*sin(y*4.0+ph*0.6))
+0.007*sin(y*27.0+ph*2.3+1.4*sin(y*11.0+ph))+0.003*sin(y*67.0+ph*3.1);
}
vec3 tint(float h,float v,float kind,vec3 ca,vec3 cb,vec3 cc,vec3 cd){
if(kind<0.5){
vec3 warm=mix(ca,ca*vec3(1.0,0.80,0.62),v);
vec3 pink=mix(cb,hx(255.0,64.0,72.0),v*0.7);
vec3 cool=mix(cc,hx(70.0,226.0,214.0),smoothstep(0.5,0.9,v));
vec3 c=mix(warm,pink,smoothstep(0.50,0.56,h));
c=mix(c,cool,smoothstep(0.74,0.80,h));
c=mix(c,cd*0.80,smoothstep(0.93,0.97,h));
return c;
}
if(kind<1.5){
vec3 lamp=mix(ca,cd,0.30*v);
float sw=smoothstep(0.46,0.54,abs(fract(uTime/46.0+v*5.0)-0.5)*2.0);
vec3 sig=mix(hx(255.0,62.0,70.0),hx(60.0,232.0,164.0),sw);
return mix(lamp,sig,step(0.80,h));
}
if(kind<2.5)return mix(cd,ca,0.15+0.45*v)*0.85;
return mix(hx(255.0,48.0,60.0),hx(255.0,96.0,64.0),v*0.6);
}
vec3 bokeh(vec2 q,float shift,vec2 cell,vec2 rr,vec3 band,float seed,float kind,vec4 md,vec3 ca,vec3 cb,vec3 cc,vec3 cd){
if(abs(q.y-band.x)>band.y*2.3+cell.y)return vec3(0.0);
vec2 g=q/cell;
g.x+=mod(shift/cell.x,89.0);
vec2 b0=floor(g-0.5);
vec2 ctr=vec2(0.5*uSize.x/uSize.y+g.x*cell.x-q.x,0.5);
float glassK=step(0.95,md.x);
float sx=1.0-0.15*glassK*smoothstep(0.6,1.0,uWind);
float reach=0.54*min(cell.x,cell.y);
vec4 kk=floor(89.0*cell.x/vec4(1.2,3.6,2.8,7.0)+0.5)*(TAU/89.0);
vec3 acc=vec3(0.0);
for(int i=0;i<4;i++){
float fi=float(i);
vec2 id=b0+vec2(mod(fi,2.0),floor(fi*0.5));
vec2 j=h22(vec2(mod(id.x,89.0),id.y)*1.31+seed);
vec2 c=id+0.5+(j-0.5)*0.9;
vec2 dv=(g-c)*cell;
dv.x*=sx;
float d0=length(dv);
if(d0<reach){
float h=fract(j.x*57.7+j.y*131.3);
float crowd=0.78+0.42*sin(c.x*kk.x+2.0*sin(c.x*kk.y));
float on=smoothstep(0.0,0.10,exp(-sq((c.y*cell.y-band.x)/band.y))*band.z*crowd-h);
if(on>0.0){
float h2=fract(h*37.31);
float h3=fract(h*91.73);
float h4=fract(h*173.9);
float h5=fract(h*251.3);
float r0=mix(rr.x,rr.y,h2);
float r=min(max(r0*md.x,md.w),reach*0.8);
float d=max(d0,length(dv+(c*cell-ctr)*(r*0.45*glassK)));
float e=max(r*md.y,0.0005);
float a=smoothstep(r+e,r-e,d);
float rim=md.z*smoothstep(0.006,0.028,r);
a*=0.62+0.05*d/r+rim*(0.55*smoothstep(r*0.78,r*0.95,d)+0.09*dot(dv,vec2(0.6,-0.8))/r+0.03*sin(d/r*19.0+h3*20.0));
a+=glassK*(0.10+0.42*(1.0-md.z))*sat(1.0-d/reach);
float dn=0.5+0.5*sin(c.x*kk.z+1.7*sin(c.x*kk.w+4.0));
float hc=mix(h3*0.66,0.30+h3*0.70,smoothstep(0.35,0.65,dn));
float tw=0.80+0.20*sin(TAU*fract(uTime*(0.04+0.07*h4)+h3*9.0));
float gain=on*tw*(0.50+1.25*h5*h5)*pow(r0*md.x/r,1.3);
float cy=c.y*cell.y;
gain*=mix(0.18,1.0,smoothstep(0.47,0.65,cy))*mix(1.0,0.60,smoothstep(0.82,0.96,cy));
acc+=tint(hc,h4,kind,ca,cb,cc,cd)*(a*gain);
}
}
}
return acc;
}
float blocks(vec2 q,float flow,float wid,vec2 hh,float seed,float blur){
float bx=q.x/wid+mod(flow/wid,64.0)+0.5;
float bi=floor(bx);
float bf=bx-bi;
float e=blur/wid;
float ha=h21(vec2(mod(bi-1.0,64.0),seed));
float hb=h21(vec2(mod(bi,64.0),seed));
float ma=smoothstep(-blur,blur,q.y-(HZ-hh.x-(hh.y-hh.x)*ha*ha));
float mb=smoothstep(-blur,blur,q.y-(HZ-hh.x-(hh.y-hh.x)*hb*hb));
return mix(ma,mb,smoothstep(0.5-e,0.5+e,bf));
}
vec3 scene(vec2 q,vec4 md,float inDrop,float hazeK){
float t=uTime;
float sd=fract(uSeed*0.1371)*19.0;
float L=1.45*day4f(0.84,0.62,0.90,1.0)*(1.0+0.14*uLevel)*(1.0-0.12*uSoft);
float many=0.88*day4f(0.86,0.62,1.0,1.0);
vec3 ca=day4(hx(255.0,170.0,86.0),hx(255.0,192.0,106.0),hx(255.0,146.0,76.0),hx(255.0,158.0,58.0));
vec3 cb=day4(hx(255.0,122.0,146.0),hx(255.0,108.0,104.0),hx(255.0,84.0,146.0),hx(255.0,56.0,142.0));
vec3 cc=day4(hx(110.0,184.0,255.0),hx(128.0,210.0,226.0),hx(96.0,152.0,255.0),hx(50.0,144.0,255.0));
vec3 cd=day4(hx(255.0,230.0,200.0),hx(255.0,238.0,210.0),hx(255.0,222.0,194.0),hx(255.0,224.0,190.0));
vec3 top=day4(hx(22.0,40.0,84.0),hx(54.0,76.0,106.0),hx(24.0,34.0,86.0),hx(6.0,8.0,24.0));
vec3 mid=day4(hx(44.0,74.0,128.0),hx(88.0,114.0,142.0),hx(46.0,58.0,128.0),hx(15.0,16.0,50.0));
vec3 low=day4(hx(146.0,146.0,190.0),hx(166.0,186.0,202.0),hx(126.0,100.0,160.0),hx(38.0,25.0,76.0));
vec3 gnd=day4(hx(28.0,42.0,78.0),hx(60.0,80.0,104.0),hx(26.0,28.0,70.0),hx(12.0,11.0,34.0));
vec3 bFar=day4(hx(54.0,80.0,130.0),hx(84.0,108.0,136.0),hx(44.0,48.0,104.0),hx(14.0,13.0,40.0));
vec3 bNear=day4(hx(34.0,54.0,98.0),hx(68.0,92.0,120.0),hx(26.0,30.0,74.0),hx(9.0,9.0,28.0));
float dk=day4f(0.4,1.0,0.25,0.0)*inDrop;
float y=q.y;
float k=sat(y/HZ);
vec3 col=mix(top,mid,smoothstep(0.0,0.70,k));
col=mix(col,low,smoothstep(0.45,1.0,k))*(1.0+0.30*dk);
float asp=uSize.x/uSize.y;
float gx=q.x/asp-day4f(0.22,0.50,0.80,0.80);
vec3 glowC=day4(hx(255.0,176.0,156.0),hx(255.0,255.0,255.0),hx(255.0,112.0,132.0),hx(255.0,112.0,132.0));
col+=glowC*day4f(0.20,0.0,0.30,0.0)*exp(-sq(gx/0.40))*exp(-sq((y-HZ+0.07)/0.17));
col=mix(col,bFar,blocks(q,uFlow*0.12,0.105,vec2(0.06,0.27),71.0+sd,0.045)*day4f(0.55,0.50,0.62,0.66));
float fx2=q.x+mod(uFlow*0.20,12.566);
float ridge=0.085+0.050*sin(fx2*3.0+sd)+0.034*sin(fx2*7.5+sd*1.7+1.3*sin(fx2*3.0))+0.016*sin(fx2*17.0+sd*2.3);
col=mix(col,bNear*(1.0-0.25*dk),smoothstep(-0.05,0.05,y-(HZ-ridge))*day4f(0.66,0.60,0.78,0.80));
float dy=y-HZ-0.02;
col=mix(col,mix(gnd,low,0.30*exp(-max(dy,0.0)*9.0))*(1.0-0.25*dk),smoothstep(-0.05,0.07,dy));
float mist=exp(-sq((dy+0.035*sin(fx2*1.5+1.0+sd))/0.20))*day4f(0.56,0.44,0.20,0.10)*mix(0.82,1.0,hazeK);
col=mix(col,low,mist);
float fx=q.x+mod(uFlow*0.30,13.35);
float n1=0.5+0.5*sin(fx*2.3533+1.7*sin(fx*0.9413+4.0));
float n2=0.5+0.5*sin(fx*1.4119+2.0+1.3*sin(fx*2.3533+1.0));
vec3 dist=mix(ca,mix(cb,cc,smoothstep(0.55,0.9,n2)),smoothstep(0.35,0.65,n1)*0.8);
float crowd=0.62+0.38*sin(fx*5.1771+2.0*sin(fx*1.8826));
float hz=(exp(-sq(dy/0.15))*0.13*crowd+exp(-sq(dy/0.36))*0.04)*mix(0.90,1.0,hazeK);
float ry=dy-0.05;
float wetK=smoothstep(0.0,0.10,ry)*exp(-max(ry,0.0)*4.5)*sq(sq(0.5+0.5*sin(fx*7.0597+1.5*sin(fx*3.2945+sd))));
col+=mix(dist,cb,0.25)*(hz+wetK*0.30)*L;
float fl=uFlow;
float bst=mix(mix(1.10,0.88,hazeK),1.15,inDrop);
col+=bokeh(q,fl*0.10,vec2(0.036,0.036),vec2(0.0060,0.0115),vec3(HZ-0.11,0.065,0.80*many),7.0+sd,0.0,md,ca,cb,cc,cd)*(0.60*L*bst*(1.0-0.4*uSoft));
col+=bokeh(q,fl*0.16,vec2(0.075,0.075),vec2(0.015,0.029),vec3(HZ-0.07,0.10,0.85*many),3.0+sd,0.0,md,ca,cb,cc,cd)*(0.90*L*bst*(1.0-0.3*uSoft));
col+=bokeh(q,fl*0.30,vec2(0.150,0.150),vec2(0.026,0.044),vec3(HZ-0.01,0.10,1.00*many),11.0+sd,0.0,md,ca,cb,cc,cd)*(1.25*L*bst);
col+=bokeh(q,fl*0.36,vec2(0.157,0.157),vec2(0.032,0.052),vec3(HZ+0.02,0.12,0.95*many),29.0+sd,0.0,md,ca,cb,cc,cd)*(1.15*L*bst);
col+=bokeh(q,fl*0.42,vec2(0.163,0.163),vec2(0.036,0.058),vec3(HZ+0.05,0.15,0.90*many),23.0+sd,0.0,md,ca,cb,cc,cd)*(1.05*L*bst);
col+=bokeh(q,fl*0.26,vec2(0.200,0.150),vec2(0.030,0.042),vec3(HZ-0.18,0.045,0.90),17.0+sd,1.0,md,ca,cb,cc,cd)*(1.05*L*bst);
col+=bokeh(q,fl*0.50+t*0.022,vec2(0.300,0.140),vec2(0.028,0.040),vec3(HZ+0.05,0.03,0.85),31.0+sd,2.0,md,ca,cb,cc,cd)*(1.20*L*bst);
col+=bokeh(q,fl*0.55-t*0.017,vec2(0.340,0.140),vec2(0.026,0.038),vec3(HZ+0.10,0.03,0.80),43.0+sd,3.0,md,ca,cb,cc,cd)*(1.10*L*bst);
if(inDrop<0.5){
col+=bokeh(q,fl*0.75,vec2(0.300,0.300),vec2(0.062,0.092),vec3(HZ+0.10,0.17,0.66*many),37.0+sd,0.0,md,ca,cb,cc,cd)*(0.44*L*bst);
col+=bokeh(q,fl*1.10,vec2(0.500,0.500),vec2(0.105,0.150),vec3(HZ+0.20,0.25,0.52*many),53.0+sd,0.0,md,ca,cb,cc,cd)*(0.24*L*bst);
}
return col;
}
vec4 bead(vec2 pw,float n,float seed,float dens,float asp,float topK){
vec2 g=pw*n;
vec2 id=floor(g);
float hc=h21(id+seed);
float period=mix(50.0,130.0,hc);
float ph=uTime/period+hc*17.0;
float k=floor(ph);
float f=ph-k;
vec2 ik=id+seed+vec2(mod(k,37.0)*0.731,mod(k,41.0)*0.377);
float hs=h21(ik+1.7);
vec2 j=h22(ik+4.3);
float rc=0.05+0.27*hs*hs;
vec2 c=(id+0.5+(j-0.5)*(0.94-2.9*rc))/n;
float lowK=smoothstep(0.28,0.74,c.y);
float sideK=smoothstep(0.09,0.01,min(c.x,asp-c.x)/asp)*smoothstep(0.20,0.42,c.y);
float busy=max(lowK,0.75*sideK);
float life=smoothstep(0.0,0.7,f*period)*smoothstep(period,period-6.0,f*period)*(0.72+0.28*f);
float hp=fract(hs*57.3+hc*3.1);
float keep=smoothstep(0.0,0.14,dens*mix(topK,1.0,busy)-hp);
return vec4(c,rc/n*life*keep*mix(0.80,1.0,busy),hs);
}
vec2 dropUV(vec2 o,float r,float h,float kw,float sag){
vec2 v=-o/max(r,0.00001)*vec2(1.0+0.16*(h-0.5),1.0-0.16*(h-0.5));
v.x+=kw*v.y;
v.y*=v.y>0.0?1.0-0.10*sag:1.0+0.08*sag;
v.x*=1.0+0.14*sag*sat(-v.y);
return v;
}
float eaten(vec2 off,float rc,vec4 tr,vec2 hd,float slant,float xs,float W){
float ox=off.x+slant*off.y;
float fc=fract(xs+ox/W);
float inLane=smoothstep(rc/W,rc/W*1.4+0.02,min(fc,1.0-fc));
float trail=smoothstep(tr.y*1.10+rc,tr.y*0.55,abs(tr.x+ox))*smoothstep(-rc,rc,tr.z-off.y)*tr.w;
float head=smoothstep(hd.y*1.3+rc,hd.y*0.8,length(vec2(ox+hd.x,off.y-tr.z)));
return max(trail,head)*inLane;
}
float merged(vec2 off,float rBig,float rSmall,float px,vec2 cs,float n){
vec2 f=abs(fract(cs*n)-0.5);
float inCell=smoothstep(rSmall*n,rSmall*n*1.5+0.02,0.5-max(f.x,f.y));
return smoothstep(rBig*1.30+rSmall,rBig*0.90,length(off))*step(px,rBig)*inCell;
}
vec4 sceneMain(vec2 xy){
vec2 uv=xy/uSize;
float asp=uSize.x/uSize.y;
vec2 p=vec2(uv.x*asp,uv.y);
float px=1.0/uSize.y;
float t=uTime;
float rainK=mix(0.6,uRain,smoothstep(0.0,0.35,uRain));
float quiet=1.0-0.4*uSoft;
float windK=smoothstep(0.5,1.0,uWind);
float slant=0.04+0.62*windK;
float gs=gust(p.x,t);
float sd0=fract(uSeed*0.1371)*19.0;
float xw=p.x+slant*(p.y-0.55);
vec2 w1=p*95.0;
vec2 warpA=vec2(sin(w1.y*1.1+1.7*sin(w1.x*0.8+2.0)),sin(w1.x*1.2+1.9*sin(w1.y*0.7+5.0)))*0.0017;
vec2 w2=p*262.0;
vec2 warpB=vec2(sin(w2.y+2.1*sin(w2.x*0.7)),sin(w2.x*1.2+1.3*sin(w2.y*0.9+1.0)))*0.0006;
vec2 w0=p*23.0;
vec2 warpH=vec2(sin(w0.y*1.3+1.6*sin(w0.x*0.9+1.0)),sin(w0.x*1.1+1.8*sin(w0.y*0.8+3.0)))*0.0034;
vec2 pA=p+warpA;
vec2 pB=pA+warpB;
vec2 pH=pA+warpH;
float patchy=0.50+1.00*vn(p*2.7+sd0+31.0);
float dens=(0.30+0.85*rainK)*(0.80+0.40*gs)*patchy;
vec4 dH=bead(pH,7.0,45.0+sd0,0.42*dens,asp,0.10);
vec4 dA=bead(pA,13.0,5.0+sd0,0.50*dens,asp,0.20);
vec4 dB=bead(pA,27.0,15.0+sd0,0.56*dens*(1.0-0.25*uSoft),asp,0.08);
vec4 dC=bead(pB,58.0,25.0+sd0,0.50*dens*quiet,asp,0.0);
vec2 oH=dH.xy-pH;
vec2 oA=dA.xy-pA;
vec2 oB=dB.xy-pA;
vec2 oC=dC.xy-pB;
float eH=0.0;
float eA=0.0;
float eB=0.0;
float eC=0.0;
float clearK=0.0;
float lensX=0.0;
vec2 bv=vec2(0.0);
float br=1.0;
float bm=0.0;
float bg=0.5;
vec2 tv=vec2(0.0);
float tr=1.0;
float tm=0.0;
float tg=0.5;
for(int i=0;i<2;i++){
float fl=float(i);
float W=mix(0.215,0.128,fl);
float sd=29.0+fl*23.0+sd0*1.37;
float xs=xw/W+fl*0.37+3.0;
float li=floor(xs);
float lx=(xs-li)*W;
float h0=h21(vec2(li,sd));
float period=mix(30.0,54.0,h0);
float ph=t/period+h21(vec2(li,sd+3.3))*9.0;
float k=floor(ph);
float tau=(ph-k)*period;
float km=mod(k,61.0);
vec2 hk=h22(vec2(li+km*0.618,sd+1.7+km*0.371));
float hq=h21(vec2(li-km*0.411,sd+6.1+km));
float keep=smoothstep(0.0,0.15,rainK+0.02+0.25*windK-0.15*uSoft-hk.x);
float y0=mix(0.27,0.62,hk.y);
float r0=mix(0.0105,0.0215,hq)*mix(1.0,0.62,fl)*keep;
float stepH=mix(0.07,0.15,fract(hq*7.7));
float x0=W*(0.5+(fract(hk.y*13.3)-0.5)*0.50);
float gEnd=(-1.0+sqrt(1.0+4.0*ACC*(1.25-y0)/stepH))/(2.0*ACC);
float tGrow=2.0+2.0*fract(hq*3.3);
float rate=gEnd/(period*mix(0.30,0.48,fract(hk.x*9.1))*(1.25-y0)*0.85);
float s=max(tau-tGrow,0.0)*rate;
float n0=floor(s);
float f0=s-n0;
vec2 e0=h22(vec2(n0+li*3.7,sd+km*1.3));
float a0=e0.x*0.36;
float b0=a0+0.36+0.28*e0.y;
float m0=sat((f0-a0)/(b0-a0));
float g0=n0+smoothstep(0.0,1.0,pow(m0,0.72));
float yd=y0+stepH*g0*(1.0+ACC*g0);
float vel=4.0*m0*(1.0-m0);
float u=(p.y-y0)/stepH;
float gp=(-1.0+sqrt(1.0+4.0*ACC*max(u,0.0)))/(2.0*ACC);
float n1=floor(gp);
float f1=gp-n1;
vec2 e1=h22(vec2(n1+li*3.7,sd+km*1.3));
float a1=e1.x*0.36;
float b1=a1+0.36+0.28*e1.y;
float sPass=n1+a1+(b1-a1)*pow(invSmooth(f1),1.389);
float age=(s-sPass)/rate;
float begun=smoothstep(-0.3,0.2,u);
float passed=smoothstep(0.0,0.12,age)*begun;
float endK=smoothstep(period,period-5.0,tau);
float phw=hk.x*40.0;
float xd=x0+W*wig(yd,phw);
float dx=lx-(x0+W*wig(p.y,phw));
float grow=sqrt(smoothstep(0.0,tGrow,tau));
float rNow=r0*grow*mix(0.55,1.0,smoothstep(0.25,0.75,yd));
float rT=r0*mix(0.55,1.0,smoothstep(0.25,0.75,p.y));
float big=smoothstep(0.0025,0.0050,r0);
float dry=exp(-max(age,0.0)/13.0)*endK;
float brk=vn(vec2(p.y*34.0+phw,dx/max(rT,0.0001)*1.1+li*1.7+sd));
float wc=rT*(1.25+0.50*sin(p.y*13.0+phw+2.0*sin(p.y*5.3+phw*0.7)));
float wNow=min(wc*(0.60+0.40*dry),0.80*min(lx-dx,W-lx+dx));
float dxe=dx+rT*0.55*(brk-0.5);
float whole=smoothstep(0.0,0.30,dry*1.5-brk*0.85+0.12);
float cl=smoothstep(wNow,wNow*0.50,abs(dxe))*passed*big*whole*sqrt(dry);
clearK=max(clearK,cl);
float un=clamp(dx/max(wNow,0.00001),-1.0,1.0);
lensX+=sin(3.14159*un)*(1.0-un*un)*passed*big*exp(-max(age,0.0)/3.0)*endK*rT*0.30;
vec4 trk=vec4(dx,wc,yd-p.y,passed*big*(1.0-smoothstep(5.0,20.0,age))*endK);
float hbz=0.019+0.012*hq;
float bi=floor(p.y/hbz);
float bh=h21(vec2(bi+li*7.1,sd+km*0.77));
float yb=(bi+0.22+0.56*bh)*hbz;
float xb=x0+W*wig(yb,phw)+(fract(bh*17.3)-0.5)*rT*0.7;
float el=1.0+1.2*fract(bh*7.9);
float rb=rT*(0.22+0.38*sq(fract(bh*31.7)))*passed*big*(0.45+0.55*dry)*smoothstep(0.0,0.2,dry)*step(0.36,fract(bh*5.3));
rb=min(rb,0.22*hbz/el)*smoothstep(0.34,0.52,yb);
vec2 db=vec2(lx-xb-slant*(p.y-yb),(p.y-yb)/el);
float mb=sat((rb-length(db))/px+0.5)*step(px*0.7,rb);
if(mb>tm){tm=mb;tv=db/max(rb,0.00001);tr=rb;tg=bh;}
vec2 dd=vec2(lx-xd,p.y-yd);
float st=1.10+0.50*vel;
float rH=rNow/sqrt(0.5+0.5*st);
float A=dd.y/max(rH,0.00001);
float C=mix(dd.x,dx,sat(-A*0.6))/max(rH,0.00001);
float A2=A>0.0?A*1.05:A/st;
vec2 vs=vec2(C*(1.0+0.30*min(A2*A2,4.0)*step(A,0.0)),A2);
float fh=length(vs);
float sH=(1.0-fh)*rH;
float sb=-dd.y;
float wt=rH*0.78*(0.25+0.75*vel)*exp(-max(sb,0.0)/max(rH*(0.9+2.6*vel),0.00001))*smoothstep(-0.2*rH,0.5*rH,sb)*begun;
float sT=wt-abs(dx);
float kS=0.30*rH;
float hS=max(kS-abs(sH-sT),0.0)/max(kS,0.00001);
float sU=max(sH,sT)+hS*hS*kS*0.25;
float tw=sat(0.5+(sT-sH)/max(2.0*kS,0.00001));
float rr=mix(rH,max(wt,px),tw);
vec2 vv=mix(vs*min(1.0,2.0/max(fh,0.0001)),vec2(dx/max(wt,0.00001),0.0),tw);
vv=vv/max(length(vv),0.0001)*sat(1.0-sU/max(rr,0.00001))*mix(1.0,sat(wt/px*0.35-0.35),tw);
float ms=sat(sU/px+0.5)*step(px*0.7,rH)*mix(1.0,sat(wt/px*0.8-0.8),tw);
if(ms>0.0){bm=ms;bv=vv;br=rr;bg=hq;}
vec2 hd=vec2(dd.x,rH);
eH=max(eH,eaten(oH,dH.z,trk,hd,slant,xs,W));
eA=max(eA,eaten(oA,dA.z,trk,hd,slant,xs,W));
eB=max(eB,eaten(oB,dB.z,trk,hd,slant,xs,W));
eC=max(eC,eaten(oC,dC.z,trk,hd,slant,xs,W));
}
float kw=0.5*(slant-0.04);
float rHd=dH.z*(1.0-eH);
float rA=dA.z*(1.0-max(eA,merged(oA-oH,rHd,dA.z,px,pH+oA,7.0)));
float rB=dB.z*(1.0-max(eB,max(merged(oB-oH,rHd,dB.z,px,pH+oB,7.0),merged(oB-oA,rA,dB.z,px,pA+oB,13.0))));
float rC=dC.z*(1.0-max(eC,max(merged(oC-oH,rHd,dC.z,px,pH+oC,7.0),max(merged(oC-oA,rA,dC.z,px,pA+oC,13.0),merged(oC-oB,rB,dC.z,px,pA+oC,27.0)))));
vec2 cv=vec2(0.0);
float cr=1.0;
float cm=0.0;
float cg=0.5;
float shade=0.0;
vec2 vC=dropUV(oC,rC,dC.w,kw,0.0);
float mC=sat((1.0-length(vC))*rC/px+0.5)*sat(rC/px*0.7-0.7);
if(mC>0.0){cm=mC;cv=vC;cr=rC;cg=dC.w;}
vec2 vB=dropUV(oB,rB,dB.w,kw,0.5);
float mB=sat((1.0-length(vB))*rB/px+0.5)*sat(rB/px*0.7-0.7);
if(mB>0.0){cm=mB;cv=vB;cr=rB;cg=dB.w;}
vec2 fB=abs(fract(pA*27.0)-0.5);
shade=max(shade,exp(-max(length(vB-vec2(0.0,0.08))-1.0,0.0)*7.0)*sat(rB/px-0.5)*smoothstep(0.5,0.40,max(fB.x,fB.y)));
vec2 vA=dropUV(oA,rA,dA.w,kw,1.0);
float mA=sat((1.0-length(vA))*rA/px+0.5)*sat(rA/px-0.5);
if(mA>0.0){cm=mA;cv=vA;cr=rA;cg=dA.w;}
vec2 fA=abs(fract(pA*13.0)-0.5);
shade=max(shade,exp(-max(length(vA-vec2(0.0,0.08))-1.0,0.0)*7.0)*sat(rA/px-0.5)*smoothstep(0.5,0.40,max(fA.x,fA.y)));
vec2 vH=dropUV(oH,rHd,dH.w,kw,1.0);
float mH=sat((1.0-length(vH))*rHd/px+0.5)*sat(rHd/px-0.5);
if(mH>0.0){cm=mH;cv=vH;cr=rHd;cg=dH.w;}
vec2 fH=abs(fract(pH*7.0)-0.5);
shade=max(shade,exp(-max(length(vH-vec2(0.0,0.08))-1.0,0.0)*7.0)*sat(rHd/px-0.5)*smoothstep(0.5,0.40,max(fH.x,fH.y)));
if(tm>0.0){cm=tm;cv=tv;cr=tr;cg=tg;}
if(bm>0.0){cm=bm;cv=bv;cr=br;cg=bg;}
float fogN=fbm3(p*1.7+vec2(t*0.004,-t*0.006)+sd0);
float streak=smoothstep(0.56,0.92,vn(vec2(xw*17.0,p.y*0.8+7.0)))*(0.35+0.65*vn(vec2(xw*6.0+3.0,p.y*2.1)));
float fog=sat(0.52+0.36*fogN-0.16*streak+0.14*(1.0-uv.y)+0.2*uSoft+0.10*(gs-0.45)*(0.5+uWind));
float edgeK=clearK*(1.0-clearK)*4.0;
fog*=1.0-0.80*clearK;
vec2 wet=(warpH*0.55+warpA*0.40)*(0.55+0.45*fog)+vec2(lensX,0.0);
vec3 col=scene(p+wet,vec4(1.0,0.045+0.19*fog,1.0-0.60*fog,0.0),0.0,fog);
vec3 veil=day4(hx(104.0,126.0,170.0),hx(150.0,166.0,180.0),hx(84.0,80.0,130.0),hx(44.0,42.0,92.0));
col=mix(col,veil,fog*day4f(0.13,0.15,0.10,0.09));
col*=1.0+fog*0.10*(fogN-0.5);
col+=veil*edgeK*0.03;
col*=1.0-0.13*shade*(1.0-cm);
if(cm>0.001){
float rho2=min(dot(cv,cv),1.0);
float rho=sqrt(rho2);
float lens=0.94/sqrt(1.0-0.8836*rho2);
vec2 cc=p-cv*cr;
float bigK=smoothstep(0.004,0.018,cr);
float F=0.045+2.0*min(cr,0.03)+0.25*abs(cc.y-HZ)*bigK;
vec2 qr=vec2(cc.x,mix(cc.y,HZ,0.40*bigK))-cv*lens*F;
float fp=px/cr*F*lens;
vec3 dc=scene(qr,vec4(mix(0.90,0.55,bigK),0.30,0.10,fp*0.8),1.0,0.0);
dc+=col*day4f(0.26,0.14,0.38,0.42);
vec3 amb=day4(hx(210.0,226.0,255.0),hx(236.0,244.0,255.0),hx(190.0,190.0,255.0),hx(130.0,140.0,220.0));
vec3 gcol=mix(amb,hx(255.0,214.0,160.0),step(0.55,fract(cg*9.7))*day4f(0.5,0.2,0.7,0.9));
float gloss=sat(cr/px*0.25-0.4)*(1.0-0.35*uSoft)*(1.0-0.40*day4f(0.4,1.0,0.15,0.0));
vec2 nv=cv/max(rho,0.001);
float lit=dot(nv,vec2(-0.55,-0.83));
dc*=1.0-(0.06+0.34*sat(lit+0.3))*sq(smoothstep(0.84,1.0,rho))*sat(cr/px*0.5-0.5);
float arc=smoothstep(0.55,0.80,rho)*smoothstep(0.98,0.86,rho)*sq(sat(lit));
float cau=smoothstep(0.30,0.85,rho)*smoothstep(1.0,0.86,rho)*sq(sat(-lit));
vec2 sp=cv-vec2(-0.40+0.18*(cg-0.5),-0.46+0.14*(fract(cg*7.3)-0.5));
float glint=exp(-dot(sp,sp)/(0.004+0.006*fract(cg*3.7)))*(0.35+0.65*fract(cg*5.1));
dc+=(gcol*(arc*0.40+glint*0.85)+(dc*0.9+amb*0.08)*cau)*gloss;
col=mix(col,dc,cm);
}
float mx=max(col.r,max(col.g,col.b));
vec3 keepHue=col*((1.0-exp(-1.4*mx))/max(mx,0.0001));
col=mix(keepHue,1.0-exp(-col*1.4),0.22+0.45*smoothstep(0.7,2.2,mx));
return finish(col,xy);
}
uniform float uDim;
uniform vec4 uVeil;
uniform float uFade;
void main() {
    vec4 c = sceneMain(vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y));
    gl_FragColor = vec4(mix(c.rgb, uVeil.rgb, uVeil.a) * (1.0 - uDim), uFade);
}
