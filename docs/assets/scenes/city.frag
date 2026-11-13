// city — tools/site/agsl2glsl.mjs 가 app/src/main/assets/scenes/_lib.agsl + city.agsl 에서 만든 파일. 손으로 고치지 말 것.
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
const float WATER=0.725;
const float Z0=0.11;
const float PI=3.14159265;
const float TAU=6.2831853;
const float FOLD=48.0;
float vnp(vec2 p,vec2 per){
p=mod(p,per);
vec2 i=floor(p);
vec2 f=p-i;
vec2 u=f*f*(3.0-2.0*f);
vec2 j=mod(i+1.0,per);
float a=h21(i);
float b=h21(vec2(j.x,i.y));
float c=h21(vec2(i.x,j.y));
float d=h21(j);
return mix(mix(a,b,u.x),mix(c,d,u.x),u.y);
}
float lite(float d,float r,float px){
float rr=max(r,0.8*px);
float k=r/rr;
return exp(-d*d/(rr*rr))*k*k;
}
vec3 skyGrad(float e,vec3 lo,vec3 mid,vec3 top){
float sy=sat((e-0.02)/0.70);
vec3 c=mix(lo,mid,smoothstep(0.0,0.40,sy));
return mix(c,top,smoothstep(0.28,1.0,sy));
}
float namsan(float dx){
return 0.020+0.110*exp(-sq(dx/0.26))+0.050*exp(-sq((dx-0.46)/0.40))+0.038*exp(-sq((dx+0.42)/0.34));
}
float range(float x){
float a=1.0-abs(2.0*vnp(vec2(x*1.25,3.0),vec2(60.0,8.0))-1.0);
float b=1.0-abs(2.0*vnp(vec2(x*3.5,5.0),vec2(168.0,8.0))-1.0);
return 0.040+0.085*a*a+0.026*b+0.007*vnp(vec2(x*12.0,7.0),vec2(576.0,8.0));
}
vec4 lot(float x,float n,float seed,float tw){
float cx=x*n;
float c=floor(cx);
float f=cx-c;
float r0=h21(vec2(c,seed));
float r1=h21(vec2(c,seed+7.3));
float r3=h21(vec2(c,seed+21.1));
float tc=tw-(f-0.5)/n;
float pk=0.5-0.5*cos(tc*(TAU/0.75));
float town=sat(0.16+0.68*pk+0.18*sin(tc*(TAU/2.0)+1.0));
float h=mix(r0,r0*r0,0.6)*mix(0.50,1.0,town);
h=max(h,mix(0.64,1.0,r0)*step(0.83,pk)*step(0.35,h21(vec2(c,seed+29.0))));
float w=mix(0.46,0.98,r1)*mix(1.0,0.66,smoothstep(0.45,0.75,h));
if(h21(vec2(c,seed+61.0))<0.12)h=-1.0;
return vec4(c,(f-(1.0-w)*r3)/w,w/n,h);
}
vec3 layer(vec3 col,vec2 q,vec4 g,vec4 wn,vec4 lg,float tw,vec3 body,vec3 haze,vec3 sky,vec4 sunC,float px){
vec4 lt=lot(q.x,g.x,g.w,tw);
if(lt.w<0.0)return col;
float id=lt.x;
float u=lt.y;
float wB=lt.z;
float y=q.y;
float H=g.y+(g.z-g.y)*lt.w;
if(y>H*1.25+0.045||u<-0.05||u>1.05)return col;
float t=uTime;
float r2=h21(vec2(id,g.w+13.9));
float r4=h21(vec2(id,g.w+41.0));
float r5=h21(vec2(id,g.w+55.0));
float r6=h21(vec2(id,g.w+33.0));
vec2 bo=vec2(fract(id*0.6180)*61.0,fract(id*0.3170)*37.0+g.w*3.0);
float ch=1.0/wn.x;
float sw=step(0.30,r6)*(0.14+0.24*fract(r6*9.1));
float front=step(u,1.0-sw);
float uf=u/(1.0-sw);
float wF=wB*(1.0-sw);
float nc=max(2.0,floor(wF/wn.y));
float kind=fract(r2*7.31);
float tall=step(0.50,lt.w);
float hf=abs(uf-0.5)*front+0.5*(1.0-front);
float top=H;
if(tall>0.5){
if(kind<0.45){
top-=ch*2.5*step(0.24,hf)+ch*3.0*step(0.38,hf);
}else if(kind<0.75){
top-=ch*(2.0+3.0*r4)*step(0.30,hf);
}else{
top-=min((1.0-uf)*wF*0.30,ch*3.0)*front;
}
}else if(kind<0.16){
top-=ch*(1.5+2.0*r4)*step(0.33,hf);
}else if(kind<0.24){
top-=min((1.0-uf)*wF*0.25,ch*2.0)*front;
}else if(kind<0.46){
top+=ch*1.8*step(0.50,uf)*step(uf,0.80)*front;
}else if(kind<0.52){
top-=H*0.08*smoothstep(0.28,0.50,hf);
}
float roofY=top;
float ru=u*wB/(wn.y*3.3)+r4*3.0;
float rb=h21(vec2(floor(ru),5.1)+bo);
float fr=ru-floor(ru);
float rw0=0.20+0.45*fract(rb*3.1);
top+=ch*(0.40+0.80*fract(rb*7.7))*step(0.60,rb)*step(0.45,fract(r6*3.9))*step(0.12,fr)*step(fr,0.12+rw0)*step(0.10,u)*step(u,0.90);
float inX=min(u,1.0-u)*wB;
float a=sat(inX/px+0.5)*sat((top-y)/px+0.5);
float beacon=0.0;
if(fract(kind*5.3)>0.62-0.30*tall&&lt.w>0.30){
float ax=(uf-(kind>0.9?0.34:0.5))*wF;
float ah=H+ch*(1.5+2.5*r4);
float line=sat(1.0-abs(ax)/(px*0.7))*step(top-px,y)*sat((ah-y)/px)*min(1.0,0.0008/px);
a=max(a,line*0.85);
float pulse=0.5+0.5*sin(mod(t*(0.9+0.5*r4),TAU)+r2*40.0);
beacon=lite(length(vec2(ax,y-ah)),0.0018,px)*(0.25+0.75*pulse*pulse);
}
if(a<0.002&&beacon<0.004)return col;
vec3 tint=mix(hx(255.0,214.0,196.0),hx(196.0,226.0,236.0),fract(r2*3.7));
vec3 bd=mix(body,body*tint*1.25,0.16)*mix(0.92,1.10,fract(r4*6.1));
float sunK=lg.z;
float side=mix(1.0-uf,uf,sunK*0.5+0.5);
float ell=mix(sunC.a+0.22*side*side,mix(0.20,0.90,sat(0.5+0.9*sunK)),1.0-front)*mix(0.78,1.08,sat(y/max(H,0.001)));
bd=bd*(0.80+0.18*ell)+sunC.rgb*ell*0.30;
float us=sat((u-(1.0-sw))/max(sw,0.01));
bd*=mix(1.0,(0.95+0.10*us)*(1.0-0.07*step(0.62,fract(y*wn.x))*smoothstep(2.5,5.0,ch/px)),1.0-front);
float rim=sat(1.0-(1.0-side)*wF/(px*1.6))*front+0.6*sat(1.0-(top-y)/(px*1.4));
bd+=sunC.rgb*rim*0.14;
float wx=uf*nc;
float wy=y*wn.x;
vec2 wid=floor(vec2(wx,wy));
vec2 wf=vec2(wx,wy)-wid;
float cw=wF/nc;
float office=step(0.42,r5);
float tower=step(0.80,r5);
float ribs=office*(1.0-tower)*step(0.5,fract(r5*7.1));
float wl=mix(mix(mix(0.05,0.03,office),0.27,ribs),0.05,tower);
float wb=mix(mix(mix(0.30,0.36,office),0.03,ribs),0.07,tower);
float wt=mix(mix(mix(0.78,0.80,office),0.97,ribs),0.95,tower);
float ex=min(wf.x-wl,(1.0-wl)-wf.x)*cw;
float ey=min(wf.y-wb,wt-wf.y)*ch;
float lod=smoothstep(1.2,2.6,cw/px);
float win=sat(ex/px+0.5)*sat(ey/px+0.5);
float stair=step(0.70,fract((wid.x+floor(r4*4.0))*0.25+0.05))*(1.0-office);
float area=front*step(ch*1.2,y)*step(y,roofY-ch*0.9)*step(0.02,uf)*step(uf,0.98);
float fill=(1.0-2.0*wl)*(wt-wb);
win=mix(fill,win*(1.0-stair),lod)*area;
float seg=mix(4.0,2.0+floor(r4*5.0),office);
float gx=floor((wid.x+floor(r4*4.0))/seg);
float rg=h21(vec2(gx,wid.y)+bo+7.7);
float rw=h21(wid+bo);
float rf=h21(vec2(wid.y,3.3)+bo.yx);
float lb=lg.x*mix(0.30,1.20,fract(r5*11.3));
float pOn=lb*mix(0.86,0.50+0.90*step(0.70,rf),office)*step(0.14*office,rf);
float fld=vnp(vec2(wid.x*0.13,wid.y*0.47)+bo,vec2(64.0,64.0));
float key=mix(rg,0.65*(1.0-fld)+0.35*rg,tower);
float on=smoothstep(-0.012,0.012,pOn+0.03*sin(mod(t*(0.08+0.06*rg),TAU)+rg*63.0)-key);
float room=mix(step(0.22,rw)*(0.45+0.55*rw),0.42+0.34*rw+0.12*rg,office);
float bright=on*mix(0.80*(0.45+0.55*lb),room,lod);
float warm=mix(step(0.14,fract(rg*7.7)),step(0.72,fract(rg*7.7)),office);
vec3 lamp=mix(hx(208.0,230.0,255.0),hx(255.0,212.0,150.0),warm);
lamp=mix(lamp,lamp*mix(hx(255.0,190.0,130.0),hx(255.0,244.0,220.0),fract(rg*3.3))*1.10,0.55);
float glint=smoothstep(0.62,0.92,fld+0.10*sin(mod(t*0.11,TAU)+id))*(0.35+0.65*rw)*lod;
float sheen=smoothstep(0.30,0.74,vnp(vec2(wx*(0.14+0.14*r6)+wy*(fract(r4*9.3)-0.5)*0.36,wy*0.035)+bo.yx,vec2(64.0,64.0))+0.11*(rw-0.5)*lod);
vec3 glassC=mix(sky*0.86,mix(haze,vec3(1.0),0.20),sheen*(0.46+0.20*tower))*mix(0.84,1.10,sat(y/max(H,0.001)));
bd=mix(bd,mix(bd*0.74,glassC,0.50+0.22*tower),win*(0.36+0.54*lg.y));
bd+=vec3(1.0,0.97,0.90)*glint*win*0.20*lg.y;
float fogK=sat(wn.z+wn.w*exp(-y*26.0));
vec3 c=mix(bd,haze,fogK);
float spill=area*(0.14+0.06*tower)*lod;
c+=lamp*bright*(win+spill*(1.0-win))*lg.w;
c+=lamp*lb*0.035*lg.w*front;
float cy=roofY-ch*0.80-y;
vec3 crownC=mix(hx(255.0,232.0,196.0),mix(hx(126.0,226.0,222.0),hx(255.0,150.0,172.0),step(0.5,fract(r6*7.9))),step(0.5,fract(r6*2.9)));
float crown=tall*step(kind,0.75)*front*step(0.04,uf)*step(uf,0.96)*(sat((ch*0.26-abs(cy))/px+0.5)+0.20*exp(-abs(cy)/(ch*2.4)));
c+=mix(crownC,vec3(1.0),0.40)*crown*smoothstep(0.30,0.80,lg.x)*0.36*lg.w;
c+=haze*lg.x*0.16*exp(-y*34.0);
col=mix(col,c,a);
col+=hx(255.0,72.0,62.0)*beacon*smoothstep(0.0,0.5,lg.x);
return col;
}
vec4 dim(float x,float e,vec4 g,vec2 wn,float lit,float tw,vec2 sft,float s){
vec4 lt=lot(x,g.x,g.w,tw);
if(lt.w<0.0)return vec4(0.0);
float id=lt.x;
float u=lt.y;
float wB=lt.z;
float H=g.y+(g.z-g.y)*lt.w;
float inX=min(u,1.0-u)*wB;
float edge=sat(inX/sft.x+0.5);
float cov=edge*sat((H-e)/sft.y+0.5);
float r5=h21(vec2(id,g.w+55.0));
float office=step(0.42,r5);
float lb=lit*mix(0.30,1.20,fract(r5*11.3));
float nc=max(2.0,floor(wB/wn.y))*0.5;
float su=u*nc;
float ci=floor(su);
float rc=h21(vec2(ci+fract(id*0.6180)*61.0,fract(id*0.3170)*37.0+g.w*3.0));
float rd=fract(rc*13.7);
float w0=0.08+0.34*sq(fract(rc*5.1));
float wd=w0+sft.x*nc/wB;
float on=smoothstep(0.92-lb*0.60,1.0-lb*0.60,rc)*sat(lb*6.0);
float fc=su-ci;
float stripe=exp(-sq((fc-0.5-(rd-0.5)*0.4)/wd))*on*(0.35+0.65*rd)*(w0/wd);
stripe*=smoothstep(0.0,0.16,fc)*smoothstep(1.0,0.84,fc);
float reach=0.016+0.46*s;
float fall=smoothstep(H+reach,H-0.5*reach,e)*(0.30+0.70*smoothstep(0.004,0.030,e))*edge;
float warm=mix(0.86,0.30,office)+0.3*(fract(r5*23.7)-0.5);
return vec4(cov,stripe*fall,sat(warm),lb*fall);
}
vec4 bridge(vec2 q,float px,float lit){
float deck=0.060;
float span=0.56;
float sx=q.x/span;
float sf=fract(sx)-0.5;
float th=0.0042+0.0050*sq(1.0-2.0*abs(sf));
float a=sat((deck-q.y)/px+0.5)*sat((q.y-(deck-th))/px+0.5);
float pw=0.0052+0.0040*smoothstep(deck-0.022,deck-th,q.y);
float pier=sat((pw-abs(sf)*span)/px+0.5)*step(q.y,deck)*step(-0.002,q.y);
a=max(a,pier);
float af=fract(sx);
float arcY=deck+0.044*(1.0-sq(af*2.0-1.0));
float rib=max(px*1.0,0.0011)+0.0012*sq(af*2.0-1.0);
float arc=sat((rib-abs(q.y-arcY))/px+0.5)*step(deck,q.y)*min(1.0,0.0018/px);
float hang=sat(1.0-abs(fract(q.x/0.0224)-0.5)*0.0224/(px*0.5))*step(deck,q.y)*step(q.y,arcY)*min(1.0,0.0005/px);
a=max(a,max(arc,hang*0.42));
float lw=0.070;
float lf=(fract(q.x/lw)-0.5)*lw;
float lh=deck+0.0115;
float post=sat(1.0-abs(lf)/(px*0.5))*step(deck,q.y)*step(q.y,lh)*min(1.0,0.0005/px);
a=max(a,post*0.65);
float ld=length(vec2(lf,q.y-lh));
float lamp=(lite(ld,0.0013,px)+0.34*lite(ld,0.0054,px))*lit;
float carY=deck+0.0024;
float c1=q.x*24.0-mod(uTime*0.72,1344.0);
float i1=mod(floor(c1),1344.0);
float d1=length(vec2((fract(c1)-(0.2+0.6*h21(vec2(i1,3.3))))/24.0,q.y-carY));
float head=lite(d1,0.0011,px)*step(0.50,h21(vec2(i1,9.1)));
float c2=q.x*21.0+mod(uTime*0.504,1176.0);
float i2=mod(floor(c2),1176.0);
float d2=length(vec2((fract(c2)-(0.2+0.6*h21(vec2(i2,5.7))))/21.0,q.y-carY-0.0010));
float tail=lite(d2,0.00105,px)*step(0.48,h21(vec2(i2,2.9)));
float ty=deck+0.0036;
float tx=mod(q.x-mod(uTime*0.055+uSeed*0.7,8.0),8.0)-1.0;
float inTrain=step(0.0,tx)*step(tx,0.50);
float body=inTrain*sat((0.0036-abs(q.y-ty))/px+0.5);
a=max(a,body);
lamp*=1.0-body;
float car=fract(tx/0.0625);
float tw=inTrain*sat((0.0015-abs(q.y-ty-0.0008))/px+0.5)*step(0.06,car)*step(car,0.94)*step(0.28,fract(car*6.0))*min(1.0,0.0030/px);
float nose=lite(length(vec2(tx-0.50,q.y-ty)),0.0015,px)*step(-0.01,tx);
float hxc=(floor(q.x/0.0224)+0.5)*0.0224;
float pearl=lite(length(vec2(q.x-hxc,q.y-deck-0.044*(1.0-sq(fract(hxc/span)*2.0-1.0)))),0.00085,px);
float dark=0.12+0.88*lit;
return vec4(a,lamp+(head*0.9*(1.0-body)+nose*0.8)*dark+pearl*0.55*lit,tail*dark*(1.0-body),tw*dark);
}
vec2 trees(vec2 q,float sx,float px){
float sway=(gust(sx,uTime)-0.3)*0.5*q.y*36.0;
float x=q.x*30.0+sway+sin(mod(uTime*1.1,TAU)+q.x*(TAU*5.0))*0.05*(0.4+uWind);
float b=0.0075+0.0130*vnp(vec2(x,1.3),vec2(480.0,8.0))+0.0065*vnp(vec2(x*2.7,7.7),vec2(1296.0,16.0))+0.0030*vnp(vec2(x*7.3,2.2),vec2(3504.0,8.0));
b*=0.62+0.76*vnp(vec2(q.x*3.0,4.4),vec2(48.0,8.0));
return vec2(sat((b-q.y)/px+0.5),sat(q.y/b));
}
float rip(vec2 w,float ang,vec2 k,vec2 vel,float fw,float t){
mat2 m=rot(ang);
return vnp(m*(w*k)+mod(m*vec2(k.x,0.0)*fw,256.0)+mod(m*vel*t,256.0),vec2(256.0,256.0));
}
vec2 rowN(float xr,float fr,float r,float t){
float rr=h11(r*1.31+1.0);
float rs=fract(rr*7.3);
float len=mix(6.0,11.0,rr);
float x=xr/len+mod(fr/len+rr*61.0+t*(rs-0.5)*0.012,256.0);
float tt=mod(t*mix(0.045,0.080,rs),64.0);
float n=0.66*vnp(vec2(x,tt+r*3.7),vec2(256.0,64.0))+0.34*vnp(vec2(x*3.0+7.0,tt*1.7+r*1.9+5.0),vec2(768.0,64.0));
float cy=0.5+(vnp(vec2(x*1.25+3.0,r*1.7+9.0),vec2(320.0,64.0))-0.5)*0.40;
return vec2(n,cy);
}
float dash(vec2 rn,float fy,float dens,float es){
float sd=sqrt(sat(dens));
float k=(rn.x-(0.82-0.56*sd))/0.15;
float thick=sat(k)*mix(0.16,0.70,sd);
return sat((thick-abs(fy-rn.y))/es)*(0.45+0.55*sat(k*0.8));
}
vec3 bokeh(float qx,float s,float t,float px){
float rl=16.0/352.0;
float G=0.050;
float gx=qx/rl;
float c0=floor(gx);
float r0=floor(s/G);
vec3 acc=vec3(0.0);
for(int j=0;j<3;j++){
float rj=r0+float(j)-1.0;
for(int i=0;i<3;i++){
float fi=float(i)-1.0;
float ci=mod(c0+fi,352.0);
float id=h21(vec2(ci,rj+7.0));
float life=t*mix(0.085,0.125,id)+id*31.0;
float n=mod(floor(life),64.0);
float u=fract(life);
float env=smoothstep(0.0,0.35,u)*smoothstep(1.0,0.45,u);
vec2 hs=h22(vec2(ci+n*1.7,rj*1.3+n*0.7));
float sC=(rj+hs.y)*G;
float R=min(0.004+0.115*max(sC-0.09,0.0),0.027);
float lr=h21(vec2(ci,4.7));
float lon=smoothstep(0.24,0.34,vnp(vec2(ci*0.3125,2.5),vec2(110.0,8.0)))*step(0.08,fract(lr*9.7));
float fall=exp(-sC/(0.085+0.110*lr))*smoothstep(0.085,0.13,sC);
float th=h21(vec2(ci+n*0.9+3.0,rj-n*1.1));
float on=smoothstep(th-0.06,th+0.06,1.9*fall)*lon;
vec2 dv=vec2((gx-c0-fi-0.5-(hs.x-0.5)*0.70)*rl,s-sC);
float dd=length(dv);
float disc=sat((R-dd)/max(1.5*px,R*0.22)+0.5)*(0.82+0.18*smoothstep(R*0.5,R,dd));
float br=fract(id*3.1+n*0.11);
vec3 c=mix(hx(255.0,190.0,116.0),mix(hx(255.0,226.0,180.0),hx(255.0,168.0,150.0),step(0.6,fract(id*9.7))),step(0.55,fract(id*5.3)));
acc+=c*disc*env*on*(0.25+0.75*br*br)*(0.020/(R+0.020));
}
}
return acc;
}
vec4 sceneMain(vec2 xy){
vec2 uv=xy/uSize;
float asp=uSize.x/uSize.y;
vec2 p=vec2(uv.x*asp,uv.y);
float px=1.0/uSize.y;
float t=uTime;
float soft=sat(uSoft);
float calm=1.0-0.38*soft;
float L=day4f(0.20,0.02,0.55,0.84);
float nt=day4f(0.08,0.0,0.16,1.0);
float dayAmt=day4f(0.25,1.0,0.20,0.0);
float sunAmt=day4f(1.0,0.0,1.0,0.0);
float sunSide=day4f(-1.0,-0.7,1.0,-0.3);
vec3 skyTop=day4(hx(92.0,108.0,172.0),hx(70.0,132.0,206.0),hx(60.0,66.0,138.0),hx(13.0,15.0,44.0));
vec3 skyMid=day4(hx(170.0,150.0,198.0),hx(128.0,184.0,228.0),hx(158.0,108.0,172.0),hx(46.0,34.0,90.0));
vec3 skyLow=day4(hx(248.0,198.0,190.0),hx(236.0,226.0,218.0),hx(255.0,164.0,146.0),hx(128.0,74.0,124.0));
vec3 sunCol=day4(hx(255.0,214.0,190.0),hx(255.0,250.0,235.0),hx(255.0,192.0,132.0),hx(255.0,170.0,130.0));
vec3 glowCol=day4(hx(255.0,190.0,170.0),hx(255.0,255.0,255.0),hx(255.0,176.0,130.0),hx(255.0,140.0,118.0));
vec3 haze=mix(skyLow,glowCol,0.22+0.30*L);
vec3 warmW=hx(255.0,212.0,150.0);
vec3 coolW=hx(208.0,230.0,255.0);
vec3 red=hx(255.0,72.0,62.0);
vec4 keyC=vec4(day4(hx(255.0,190.0,160.0),hx(255.0,248.0,232.0),hx(255.0,158.0,104.0),hx(120.0,130.0,190.0))*day4f(0.70,0.80,1.00,0.30),day4f(0.44,0.66,0.34,0.40));
float lampK=smoothstep(0.04,0.45,L);
float sunX=day4f(0.19,0.5,0.80,0.80)*asp;
vec2 sunP=vec2(sunX,WATER-0.070);
float cx0=0.80*asp;
float cxm=cx0+0.040*sin(mod(uFlow*0.5,TAU));
float fx1=mod(uFlow*0.10,FOLD);
float fx2=mod(uFlow*0.17,FOLD);
float fx3=mod(uFlow*0.27,FOLD);
float fxT=mod(uFlow*0.33,16.0);
float fxB=mod(uFlow*0.40,56.0);
float o1=fx1+11.3+mod(uSeed*2.9,FOLD);
float o2=fx2+4.7+mod(uSeed*5.3,FOLD);
float o3=fx3+8.1+mod(uSeed*7.1,FOLD);
float x1=mod(p.x+o1,FOLD);
float x2=mod(p.x+o2,FOLD);
float x3=mod(p.x+o3,FOLD);
vec3 body1=day4(hx(150.0,142.0,190.0),hx(150.0,176.0,212.0),hx(128.0,98.0,156.0),hx(38.0,32.0,78.0));
vec3 body2=day4(hx(112.0,106.0,158.0),hx(118.0,146.0,190.0),hx(82.0,64.0,120.0),hx(24.0,21.0,56.0));
vec3 body3=day4(hx(80.0,76.0,124.0),hx(92.0,114.0,160.0),hx(48.0,40.0,88.0),hx(13.0,12.0,36.0));
vec3 hillCol=day4(hx(160.0,150.0,196.0),hx(146.0,176.0,212.0),hx(142.0,104.0,160.0),hx(42.0,34.0,82.0));
vec3 treeCol=day4(hx(70.0,78.0,112.0),hx(64.0,104.0,122.0),hx(40.0,40.0,78.0),hx(10.0,13.0,32.0));
vec3 bodyB=day4(hx(92.0,88.0,138.0),hx(86.0,106.0,150.0),hx(56.0,44.0,96.0),hx(17.0,15.0,42.0));
vec4 g1a=vec4(8.0,0.040,0.132,2.0);
vec4 g1b=vec4(13.0,0.034,0.112,1.0);
vec4 g2a=vec4(5.0,0.050,0.158,4.0);
vec4 g2b=vec4(8.5,0.044,0.154,3.0);
vec4 g3a=vec4(3.5,0.020,0.072,6.0);
vec4 g3b=vec4(5.5,0.022,0.088,5.0);
vec3 col;
if(p.y<=WATER){
float yUp=WATER-p.y;
col=skyGrad(yUp,skyLow,skyMid,skyTop);
float sd=length((p-sunP)*vec2(0.50,1.45));
col+=sunCol*(0.34*exp(-sd*4.4)+0.26*exp(-sd*11.0))*sunAmt;
col=mix(col,sunCol,smoothstep(0.046,0.041,length(p-sunP))*sunAmt*0.90);
float hs=length((p-vec2(0.06*asp,-0.10))*vec2(0.7,1.0));
col+=hx(255.0,244.0,214.0)*0.16*exp(-hs*1.9)*day4f(0.0,1.0,0.0,0.0);
vec2 mq=vec2(p.x*1.15+t*0.0054+uFlow*0.03+uSeed*3.1,p.y*3.4);
float mw=fbm3(mq*0.7+3.0);
float mdn=fbm(mq+vec2(mw*0.9,mw*0.6));
float rise=smoothstep(0.30,0.52,p.y);
float mshape=mdn+rise*0.40-0.12*abs(sin(p.x*1.7+uSeed));
float mc=smoothstep(0.54,0.70,mshape)*smoothstep(0.26,0.40,p.y);
float mlit=smoothstep(0.54,0.90,fbm(mq+vec2(mw*0.9,mw*0.6-0.10))+rise*0.40);
vec3 mTop=day4(hx(250.0,216.0,214.0),hx(232.0,240.0,252.0),hx(255.0,200.0,180.0),hx(78.0,60.0,116.0));
vec3 mLow=day4(hx(186.0,166.0,206.0),hx(170.0,198.0,232.0),hx(176.0,122.0,168.0),hx(40.0,34.0,84.0));
col=mix(col,mix(mTop,mLow,mlit),mc*day4f(0.66,0.70,0.76,0.50));
float hz=exp(-sat(yUp-0.03)*7.5);
col+=glowCol*hz*(0.06+0.22*L)*(1.0+0.5*uLevel);
if(nt>0.02){
float up=smoothstep(0.54,0.06,p.y);
float st=stars(p+vec2(uFlow*0.004,0.0),26.0)+stars(p*1.7+3.3,34.0)*0.6;
col+=vec3(0.86,0.90,1.0)*st*up*nt*0.85;
vec2 mp=vec2(0.885*asp,0.385);
float md=length(p-mp);
float moon=smoothstep(0.0225,0.0205,md)*smoothstep(0.0180,0.0205,length(p-mp-vec2(0.0095,-0.0050)));
col+=vec3(1.0,0.96,0.86)*(moon*0.95+0.10*exp(-md*30.0)+0.05*exp(-md*8.0))*nt*(1.0-0.85*soft);
}
float ax=mod(t*0.011+0.35+uSeed*0.3,1.5)-0.25;
vec2 ap=vec2(ax*asp,0.215-ax*0.055);
float ad=length(p-ap);
float blink=sq(0.5+0.5*sin(mod(t*1.6,TAU)));
col+=(vec3(1.0,0.95,0.9)*lite(ad,0.0010,px)*0.6+vec3(1.0,0.25,0.2)*lite(ad,0.0021,px)*blink*0.7)*(0.80*nt+0.4*L)*calm;
float wt=t*0.0098;
vec2 cp=vec2(p.x*0.80+wt+uFlow*0.015+uSeed,p.y*5.2);
float warp=fbm3(cp*0.55+7.0);
float cl=fbm(cp+vec2(warp*0.8,warp*0.5));
float band=smoothstep(0.68,0.36,p.y)*smoothstep(0.02,0.24,p.y);
float cm=smoothstep(0.50,0.74,cl)*band;
float cl2=fbm(cp+vec2(warp*0.8,warp*0.5+0.16));
float lowSide=sat((cl-cl2)*6.0+0.45+0.5*sat((p.y-0.28)*2.2));
vec3 cTop=day4(hx(206.0,186.0,216.0),hx(232.0,240.0,250.0),hx(170.0,126.0,182.0),hx(50.0,42.0,94.0));
vec3 cLow=day4(hx(255.0,204.0,194.0),hx(250.0,252.0,255.0),hx(255.0,180.0,150.0),hx(152.0,94.0,128.0));
col=mix(col,mix(cTop,cLow,lowSide),cm*day4f(0.58,0.50,0.72,0.50));
float ci=fbm(vec2(p.x*1.7+wt*1.6+31.0,p.y*13.0+warp*1.3));
col=mix(col,cLow,smoothstep(0.56,0.86,ci)*smoothstep(0.50,0.20,p.y)*smoothstep(0.0,0.12,p.y)*day4f(0.22,0.20,0.30,0.10));
if(yUp<0.275){
float xr0=mod(p.x+mod(uFlow*0.015,FOLD)+20.6,FOLD);
float rh=range(xr0);
float ra=sat((rh-yUp)/px+0.5);
col=mix(col,hillCol,ra*(0.40-0.22*exp(-yUp*22.0)));
float hx0=p.x-cxm;
float hill=namsan(hx0)+0.007*fbm3(vec2(hx0*11.0,2.0))+0.0016*vn(vec2(hx0*90.0,5.0));
float ha=sat((hill-yUp)/px+0.5);
float hk=sat(yUp/max(hill,0.001));
vec3 hc=mix(hillCol*mix(0.92,1.06,hk),haze,0.30+0.26*exp(-yUp*20.0));
hc+=keyC.rgb*0.10*smoothstep(0.80,1.0,hk)*sat(0.65+0.45*sunSide*clamp(hx0/0.12,-1.0,1.0));
col=mix(col,hc,ha);
float tB=namsan(0.0)-0.004;
float ty=yUp-tB;
float shaft=sat((mix(0.0023,0.0016,sat(ty/0.048))-abs(hx0))/px+0.5)*step(-0.01,ty)*sat((0.050-ty)/px+0.5);
float pod=max(sat((0.0058-abs(hx0))/px+0.5)*sat((0.0030-abs(ty-0.0550))/px+0.5),
sat((0.0042-abs(hx0))/px+0.5)*sat((0.0016-abs(ty-0.0505))/px+0.5));
float mast=sat((0.0012-abs(hx0))/px+0.5)*step(0.056,ty)*sat((0.076-ty)/px+0.5);
float needle=sat((0.00055-abs(hx0))/px+0.5)*step(0.075,ty)*sat((0.097-ty)/px+0.5);
float tw0=max(max(shaft,pod),max(mast,needle*0.9));
vec3 towerC=mix(hillCol*0.86,haze,0.20)+keyC.rgb*0.10*sat(0.5+2.0*sunSide*hx0/0.0023)*shaft;
towerC+=mix(coolW,hx(150.0,230.0,255.0),0.5)*L*(0.10+0.22*smoothstep(0.0,0.05,ty))*shaft;
col=mix(col,towerC,tw0);
col+=mix(coolW,hx(150.0,230.0,255.0),0.5)*pod*L*0.70;
float tp=0.5+0.5*sin(mod(t*1.05,TAU));
col+=red*lite(length(vec2(hx0,ty-0.097)),0.0018,px)*(0.25+0.75*tp*tp)*(0.3+0.7*L);
vec4 lg=vec4(L,dayAmt,sunSide,0.62*(1.0-0.25*soft));
float twx=p.x-cx0;
vec2 q1=vec2(x1,yUp-0.014);
col=layer(col,vec2(mod(x1+0.037,FOLD),q1.y),g1a,vec4(200.0,0.0040,0.40,0.20),lg*vec4(0.80,1.0,1.0,1.0),twx+fx1,body1,haze,skyTop,keyC,px);
col=layer(col,q1,g1b,vec4(200.0,0.0040,0.34,0.22),lg*vec4(0.80,1.0,1.0,1.0),twx+fx1,body1*0.96,haze,skyTop,keyC,px);
lg.w=0.92*(1.0-0.25*soft);
vec2 q2=vec2(x2,yUp-0.008);
col=layer(col,vec2(mod(x2+0.061,FOLD),q2.y),g2a,vec4(165.0,0.0050,0.14,0.34),lg,twx+fx2,body2*1.06,haze,skyTop,keyC,px);
col=layer(col,q2,g2b,vec4(165.0,0.0050,0.06,0.34),lg,twx+fx2,body2,haze,skyTop,keyC,px);
float mist=(0.45+0.55*fbm3(vec2(p.x*2.2+t*0.012+uFlow*0.2,1.0)))*exp(-yUp*26.0);
col=mix(col,haze,mist*day4f(0.60,0.26,0.38,0.30));
lg.w=1.05*(1.0-0.25*soft);
lg.x=L*1.05;
vec2 q3=vec2(x3,yUp);
col=layer(col,vec2(mod(x3+0.093,FOLD),yUp),g3a,vec4(118.0,0.0068,0.06,0.26),lg,twx+fx3,body3*1.08,haze,skyTop,keyC,px);
col=layer(col,q3,g3b,vec4(118.0,0.0068,0.0,0.26),lg,twx+fx3,body3,haze,skyTop,keyC,px);
vec2 qt=vec2(mod(p.x+fxT+2.9,16.0),yUp);
vec2 tr=trees(vec2(qt.x,yUp-0.0030),p.x,px);
float leaf=vnp(vec2(qt.x*110.0,yUp*300.0),vec2(1760.0,64.0));
vec3 trc=treeCol*mix(0.80,1.22,tr.y)*(0.90+0.20*leaf)+keyC.rgb*0.10*tr.y*tr.y*(0.4+0.6*leaf);
col=mix(col,trc,tr.x);
col=mix(col,mix(treeCol,haze,0.08+0.16*dayAmt),sat((0.0030-yUp)/px+0.5));
float rl=16.0/352.0;
float li=floor(qt.x/rl);
float lr=h21(vec2(li,4.7));
float lon=smoothstep(0.24,0.34,vnp(vec2(li*0.3125,2.5),vec2(110.0,8.0)))*step(0.08,fract(lr*9.7));
float rd=length(vec2((qt.x/rl-li-0.5)*rl,yUp-0.0058));
col+=warmW*(lite(rd,0.00125,px)+0.30*lite(rd,0.0050,px))*lampK*lon*(0.60+0.40*lr);
vec2 qb=vec2(p.x+fxB+0.25,yUp);
vec4 br=bridge(qb,px,lampK);
col=mix(col,bodyB,br.x*0.96);
col+=warmW*br.y+red*br.z+hx(220.0,255.0,232.0)*br.w*0.85;
}
}else{
float s=p.y-WATER;
float d=s/(1.0-WATER);
float zi=s+Z0;
float v=log(zi/Z0);
float xw=(p.x-0.5*asp)/zi;
float fw=uFlow*(0.33/Z0);
float dof=smoothstep(0.12,0.27,s);
float amp=(0.55+0.45*uWind+0.30*uRain)*calm;
vec2 sw1=vec2(xw*0.55+mod(fw*0.55,64.0),v*4.6-mod(t*0.125,64.0));
float n1=vnp(sw1,vec2(64.0,64.0));
float n1b=vnp(sw1+vec2(17.3,9.1),vec2(64.0,64.0));
vec2 wv=vec2(xw,v);
float n2=rip(wv,0.5,vec2(1.5,15.0),vec2(0.020,-0.050),fw,t);
float n2b=rip(wv,-0.62,vec2(1.9,19.0),vec2(-0.016,0.036),fw,t);
float f3=smoothstep(1.4,3.0,zi/(42.0*px));
float n3=(rip(wv,0.35,vec2(4.2,42.0),vec2(0.030,-0.070),fw,t)-0.5)*f3;
float R=min(60.0,Z0/(1.8*px));
float rowPx=zi/(R*px);
float vv=v*R+((n2-0.5)*2.2+(n1-0.5)*1.6+n3*0.7)*amp;
float r=floor(vv);
float fy=vv-r;
vec2 rn=rowN(xw*R,fw*R,r,t);
float es=(1.2+7.0*dof)/rowPx;
float wig=vnp(vec2(xw*R/34.0+mod(fw*R/34.0,64.0),vv*0.9),vec2(64.0,256.0))-0.5;
float dx=zi*(0.030*(n1-0.5)+0.016*(n2-0.5)+0.012*(n2b-0.5)+0.026*wig)*amp;
float dy=(zi*(0.030*(n1b-0.5)+0.050*(n2b-0.5)+0.030*(n2-0.5))+(0.006+0.10*s)*n3)*amp;
float e=max(s*0.96+dy,0.0);
float xq=p.x+dx;
vec2 sft=vec2(px*1.2+s*0.020,px*2.0+s*0.12);
vec3 skyR=skyGrad(e,skyLow,skyMid,skyTop);
float sdr=length(vec2(xq-sunX,(e-0.070)*0.40)*vec2(0.9,1.0));
skyR+=sunCol*(0.30*exp(-sdr*5.0)+0.22*exp(-sdr*14.0))*sunAmt;
skyR+=glowCol*exp(-sat(e-0.03)*7.5)*(0.06+0.22*L);
vec3 refl=skyR;
float xr0=mod(xq+mod(uFlow*0.015,FOLD)+20.6,FOLD);
refl=mix(refl,hillCol,sat((range(xr0)-e)/sft.y+0.5)*0.30);
float hx0=xq-cxm;
refl=mix(refl,mix(hillCol,haze,0.40),sat((namsan(hx0)-e)/sft.y+0.5));
float twx=xq-cx0;
vec3 kl=keyC.rgb*(keyC.a+0.10)*0.26;
vec3 lights=vec3(0.0);
vec3 lc;
vec4 m;
m=dim(mod(xq+o1+0.037,FOLD),e-0.014,g1a,vec2(200.0,0.0040),L*0.80,twx+fx1,sft,s);
refl=mix(refl,mix(body1*0.89+kl,haze,0.50),m.x);
lc=mix(coolW,warmW,m.z);
lights=lights*(1.0-0.8*m.x)+lc*(m.y+0.20*m.w)*0.40;
m=dim(mod(xq+o1,FOLD),e-0.014,g1b,vec2(200.0,0.0040),L*0.80,twx+fx1,sft,s);
refl=mix(refl,mix(body1*0.85+kl,haze,0.45),m.x);
lc=mix(coolW,warmW,m.z);
lights=lights*(1.0-0.8*m.x)+lc*(m.y+0.20*m.w)*0.40;
m=dim(mod(xq+o2+0.061,FOLD),e-0.008,g2a,vec2(165.0,0.0050),L,twx+fx2,sft,s);
refl=mix(refl,mix(body2*0.94+kl,haze,0.30),m.x);
lc=mix(coolW,warmW,m.z);
lights=lights*(1.0-0.8*m.x)+lc*(m.y+0.20*m.w)*0.62;
m=dim(mod(xq+o2,FOLD),e-0.008,g2b,vec2(165.0,0.0050),L,twx+fx2,sft,s);
refl=mix(refl,mix(body2*0.89+kl,haze,0.24),m.x);
lc=mix(coolW,warmW,m.z);
lights=lights*(1.0-0.8*m.x)+lc*(m.y+0.20*m.w)*0.62;
m=dim(mod(xq+o3+0.093,FOLD),e,g3a,vec2(118.0,0.0068),L*1.05,twx+fx3,sft,s);
refl=mix(refl,mix(body3*0.96+kl,haze,0.16),m.x);
lc=mix(coolW,warmW,m.z);
lights=lights*(1.0-0.8*m.x)+lc*(m.y+0.20*m.w)*0.72;
m=dim(mod(xq+o3,FOLD),e,g3b,vec2(118.0,0.0068),L*1.05,twx+fx3,sft,s);
refl=mix(refl,mix(body3*0.89+kl,haze,0.12),m.x);
lc=mix(coolW,warmW,m.z);
lights=lights*(1.0-0.8*m.x)+lc*(m.y+0.20*m.w)*0.72;
refl=mix(refl,treeCol,sat((0.020-e)/(px*2.0+s*0.20)+0.5)*0.9);
float qbx=xq+fxB+0.25;
float sfb=(fract(qbx/0.56)-0.5)*0.56;
float pier=sat((0.0062-abs(sfb))/sft.x+0.5)*sat((0.060-e)/sft.y+0.5);
float deckR=exp(-sq((e-0.058)/(0.004+sft.y*0.5)))*0.55;
refl=mix(refl,bodyB,max(pier,deckR)*0.80);
refl=mix(skyR,refl,1.0-0.45*smoothstep(0.03,0.24,s));
vec3 deep=day4(hx(112.0,112.0,164.0),hx(84.0,128.0,180.0),hx(58.0,50.0,104.0),hx(9.0,10.0,30.0));
col=mix(deep,refl,0.84-0.40*d);
float rk=n3*1.3+(n2+n2b-1.0)*0.5;
col+=skyLow*smoothstep(0.16,0.52,rk)*(0.035+0.050*d)*(1.0-0.6*dof)*(1.0-0.6*soft);
col*=1.0-0.10*smoothstep(-0.12,-0.50,rk)*(1.0-0.5*dof)*calm;
col*=1.0+0.10*(n1-0.5)*calm;
col*=1.0-0.30*d*d;
col+=skyLow*0.08*exp(-d*70.0);
float qtx=mod(xq+fxT+2.9,16.0);
float rl=16.0/352.0;
float li=floor(qtx/rl);
float ldx=(qtx/rl-li-0.5)*rl;
float lr=h21(vec2(li,4.7));
float lw0=max(0.0018,0.9*px);
float lwid=lw0+s*0.050;
float Lr=lampK;
float lon=smoothstep(0.24,0.34,vnp(vec2(li*0.3125,2.5),vec2(110.0,8.0)))*step(0.08,fract(lr*9.7));
float lstr=(exp(-sq(ldx/lwid))+0.16*exp(-sq(ldx/(lwid*3.0+0.004))))*sqrt(lw0*2.0/(lw0+lwid))*sq(1.0-sq(2.0*ldx/rl));
lights+=hx(255.0,196.0,122.0)*lstr*exp(-s/(0.085+0.110*lr))*(0.55+0.45*lr)*lon*Lr*1.05*(0.0018/lw0);
float lw=0.070;
float bdx=(fract(qbx/lw)-0.5)*lw;
float bw0=max(0.0018,0.9*px);
float bwid=bw0+s*0.045;
lights+=warmW*exp(-sq(bdx/bwid))*exp(-sq((s-0.060)/(0.045+0.2*s)))*Lr*0.50*(bw0*2.0/(bw0+bwid))*(0.0018/bw0);
float pathW=0.07+0.60*s;
float sunPath=exp(-sq((xq-sunX)/pathW))*sunAmt*smoothstep(0.0,0.07,s);
lights+=sunCol*sunPath*0.26;
lights*=1.0+0.25*uLevel;
float dens=sat(dot(lights,vec3(0.30,0.50,0.20))*1.6);
float lodR=smoothstep(0.15,0.45,R/60.0);
float gd=day4f(0.0,1.0,0.0,0.0)*(0.030+0.050*exp(-sq((p.x-0.22*asp)/(0.30+1.2*s))))*smoothstep(0.01,0.06,s);
col+=vec3(1.0,0.98,0.94)*dash(rn,fy,gd,es*1.8)*lodR*0.34*calm*(1.0-0.8*dof)*step(0.0005,gd);
float lit1=mix(0.30*sqrt(dens),dash(rn,fy,dens,es),lodR);
col+=lights*(mix(0.60,0.22,smoothstep(0.0,0.16,s))+1.00*lit1*(1.0-0.40*dof)*calm)*(1.0-0.25*d);
if(s>0.06&&lampK>0.01)col+=bokeh(mod(p.x+fxT+2.9,16.0),s,t,px)*lampK*0.42*(1.0+0.25*uLevel)*(1.0-0.5*soft);
}
col=mix(col,col*0.80+skyLow*0.04,soft*0.5);
return finish(col,xy);
}
uniform float uDim;
uniform vec4 uVeil;
uniform float uFade;
void main() {
    vec4 c = sceneMain(vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y));
    gl_FragColor = vec4(mix(c.rgb, uVeil.rgb, uVeil.a) * (1.0 - uDim), uFade);
}
