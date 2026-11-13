// coast — tools/site/agsl2glsl.mjs 가 app/src/main/assets/scenes/_lib.agsl + coast.agsl 에서 만든 파일. 손으로 고치지 말 것.
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
const float HZ=0.58;
const float PI=3.14159265;
const float TAU=6.2831853;
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
vec3 vndp(vec2 p,vec2 per){
p=mod(p,per);
vec2 i=floor(p);
vec2 f=p-i;
vec2 u=f*f*f*(f*(f*6.0-15.0)+10.0);
vec2 du=30.0*f*f*(f*(f-2.0)+1.0);
vec2 j=mod(i+1.0,per);
float a=h21(i);
float b=h21(vec2(j.x,i.y));
float c=h21(vec2(i.x,j.y));
float d=h21(j);
float k1=b-a;
float k2=c-a;
float k3=a-b-c+d;
return vec3(a+k1*u.x+k2*u.y+k3*u.x*u.y,du*vec2(k1+k3*u.y,k2+k3*u.x));
}
vec2 flowed(vec2 w,vec2 k,vec2 v,float fl,float t,float per){
return vec2(w.x*k.x+mod(fl*k.x+t*v.x,per),w.y*k.y+mod(t*v.y,per));
}
vec3 skyGrad(float e,vec3 top,vec3 mid,vec3 ros,vec3 low){
float k=sat(e/HZ);
vec3 c=mix(low,ros,smoothstep(0.0,0.12,k));
c=mix(c,mid,smoothstep(0.08,0.46,k));
return mix(c,top,smoothstep(0.36,1.0,k));
}
float isle(float x,float seed){
x=mod(x,32.0);
float cell=floor(x/0.5);
float h=0.0;
for(int i=0;i<3;i++){
float c=cell+float(i)-1.0;
float cm=mod(c,64.0);
float r1=h21(vec2(cm,seed));
float r2=h21(vec2(cm+0.37,seed+5.0));
float r3=h21(vec2(cm+0.71,seed+9.0));
float cx=(c+0.5+(r1-0.5)*0.7)*0.5;
float hw=mix(0.05,0.22,r2*r2);
float hh=mix(0.35,1.0,r3)*step(0.50,fract(r1*7.7+r2*3.1));
float u=(x-cx)/hw;
u+=0.40*(r3-0.5)*(1.0-u*u);
float dome=sat(1.0-u*u);
h=max(h,hh*dome*(0.50+0.50*dome));
float u2=(x-cx-hw*(0.50+0.45*r1)*sign(r2-0.45))/(hw*0.62);
float dome2=sat(1.0-u2*u2);
h=max(h,hh*(0.42+0.34*r1)*dome2*(0.50+0.50*dome2));
}
float ridge=0.58*vnp(vec2(x*9.0,seed+3.0),vec2(288.0,64.0))+0.30*vnp(vec2(x*25.0,seed+7.0),vec2(800.0,64.0))+0.12*vnp(vec2(x*70.0,seed+11.0),vec2(2240.0,64.0));
return h*(0.42+0.90*ridge);
}
float tri(vec2 p,vec2 a,vec2 b,vec2 c){
vec2 e0=b-a;
vec2 e1=c-b;
vec2 e2=a-c;
float o=sign(e0.x*(c.y-a.y)-e0.y*(c.x-a.x));
float d0=(e0.x*(p.y-a.y)-e0.y*(p.x-a.x))/length(e0);
float d1=(e1.x*(p.y-b.y)-e1.y*(p.x-b.x))/length(e1);
float d2=(e2.x*(p.y-c.y)-e2.y*(p.x-c.x))/length(e2);
return min(min(d0,d1),d2)*o;
}
vec2 boat(vec2 q,float e,float belly){
float hull=sat((0.42-abs(q.x-0.01)-1.6*max(0.035-q.y,0.0))/e+0.5)*sat((0.045-q.y)/e+0.5)*sat((q.y+0.035)/e+0.5);
float mast=sat((0.011-abs(q.x))/e+0.5)*step(0.03,q.y)*sat((1.0-q.y)/e+0.5);
vec2 qs=q+vec2(belly*sin(PI*sat(q.y)),0.0);
float mainS=tri(qs,vec2(-0.035,0.10),vec2(-0.035,0.97),vec2(-0.47,0.11));
float jib=tri(qs,vec2(0.04,0.07),vec2(0.42,0.05),vec2(0.035,0.88));
float sail=max(sat(mainS/e+0.5),sat(jib/e+0.5));
return vec2(max(hull,mast),sail);
}
float gull(vec2 q,float flap,float e){
float ax=abs(q.x);
float hw=0.12+0.22*flap;
float ht=0.05+0.42*flap;
float A=(hw-0.2025*ht)/0.2475;
float yw=A*ax+(ht-A)*ax*ax;
float th=0.050*pow(max(1.0-ax,0.0),0.7)+0.010;
float wing=sat((th-abs(q.y-yw))/e+0.5)*sat((1.0-ax)/e+0.5);
float body=sat((1.0-length(q/vec2(0.17,0.075)))*0.075/e+0.5);
return max(wing,body);
}
vec2 glitter(vec2 q,float a,float b,float seed,float dens,float bend,float edge,float t){
float v=max(log(1.0+b*q.y/a)/b+bend,0.0);
float r=floor(v);
float fy=v-r;
float sc=(exp((r+0.5)*b)-1.0)*a/b;
float hr=a+b*sc;
float rr=h11(r*1.31+seed);
float len=hr*mix(3.2,6.0,rr);
float x=q.x/len+mod(uFlow*2.0*(sc+0.05)/len+rr*61.0,256.0);
float tt=mod(t*mix(0.30,0.52,fract(rr*7.3)),64.0);
float sl=mod(t*(rr-0.5)*0.20,256.0);
float n=0.66*vnp(vec2(x+sl,tt+x*0.25+r*3.7+seed*11.0),vec2(256.0,64.0))
+0.34*vnp(vec2(x*3.0-sl*2.0+7.0,tt*2.0-x*0.25+r*1.9+seed*5.0),vec2(256.0,64.0));
float th=0.86-0.36*sqrt(sat(dens));
float k=(n-th)/0.15;
float cy=0.5+(vnp(vec2(x+3.0,r*1.7+seed),vec2(256.0,64.0))-0.5)*0.40;
float thick=sat(k)*0.25;
float dy=abs(fy-cy);
float core=sat((thick-dy)*hr/edge)*(0.45+0.55*sat(k*0.8));
float win=sin(PI*fy);
float hal=smoothstep(-1.0,0.8,k)*exp(-dy*dy*14.0)*win*win;
return vec2(core,hal);
}
float sparkle(vec2 q,float a,float b,float seed,float dens,float t,float px){
float v=log(1.0+b*q.y/a)/b;
float r=floor(v);
float fy=v-r;
float sc=(exp((r+0.5)*b)-1.0)*a/b;
float hr=a+b*sc;
float cw=hr*3.0;
float x=mod(q.x/cw+mod(uFlow*2.0*(sc+0.05)/cw+h11(r*1.7+seed)*47.0,256.0),256.0);
float c=floor(x);
float fx=x-c;
float rc=h21(vec2(c,r+seed*7.0));
float life=t*mix(0.22,0.42,rc)+rc*23.0;
float n=mod(floor(life),64.0);
float u=fract(life);
float env=sin(PI*u);
env=env*env*env;
vec2 hs=h22(vec2(c+n*3.7,r+seed*7.0+n*1.3));
float h3=h21(vec2(c-n*2.3+0.5,r*1.7+seed));
float on=smoothstep(h3-0.05,h3+0.05,dens*0.55);
vec2 d=vec2((fx-0.5-(hs.x-0.5)*0.44)*cw,(fy-0.5-(hs.y-0.5)*0.36)*hr);
float rad=max(hr*0.085,0.8*px);
float core=exp(-dot(d,d)/(rad*rad));
float ray=exp(-abs(d.y)/(rad*0.55))*exp(-abs(d.x)/(cw*0.11))*sat(1.0-abs(d.x)/(cw*0.27));
float hal=exp(-dot(d,d)/(hr*hr*0.09))*sat(1.0-length(d)/(hr*0.5));
return(core+ray*0.55+hal*0.30)*env*on*(0.5+0.5*fract(h3*13.7));
}
vec4 bokeh(vec2 p,float G,float seed,float lx,float wA,float wB,float amt,vec3 cA,vec3 cB,float t,float px){
vec4 acc=vec4(0.0);
vec2 gp=p/G;
float row=floor(gp.y);
float lim=0.50*G;
for(int j=0;j<3;j++){
float rj=row+float(j)-1.0;
float sy=(rj+0.5)*G-HZ;
float xs=mod(gp.x+mod(uFlow*2.0*(sy+0.05)/G+h11(rj*3.1+seed)*31.0,256.0),256.0);
float c0=floor(xs);
float fx=xs-c0;
for(int i=0;i<3;i++){
float fi=float(i)-1.0;
float ci=mod(c0+fi,256.0);
float id=h21(vec2(ci,rj+seed*5.0));
float life=t*mix(0.14,0.28,id)+id*31.0;
float n=mod(floor(life),64.0);
float u=fract(life);
float env=smoothstep(0.0,0.35,u)*smoothstep(1.0,0.45,u);
vec2 hs=h22(vec2(ci+n*1.7,rj*1.3+seed+n*0.7));
float dx=(fx-fi-hs.x)*G;
float dy=(gp.y-rj-hs.y)*G;
float sC=p.y-dy-HZ;
float cx=p.x-dx;
float Ri=(0.005+0.105*(sC-0.13))*mix(0.85,1.15,fract(id*5.3+n*0.37));
float R=clamp(Ri,0.003,lim);
float m=max(exp(-sq((cx-lx)/(wA+wB*max(sC,0.0)))),0.05);
float th=h21(vec2(ci+n*0.9+3.0,rj-n*1.1));
float on=smoothstep(th-0.08,th+0.08,m*amt)*smoothstep(lim*1.20,lim*0.95,Ri)*smoothstep(lim*0.30,lim*0.50,Ri);
float dd=length(vec2(dx,dy));
float disc=sat((R-dd)/max(1.5*px,R*0.20)+0.5)*(0.90+0.10*smoothstep(R*0.5,R,dd));
float br=fract(id*3.1+n*0.11);
br=0.16+0.84*br*br;
float a=disc*env*on*br*(0.030/(R+0.030));
acc+=vec4(mix(mix(cB,cA,sat(br*1.3-0.1+0.4*(fract(id*9.7)-0.5))),vec3(1.0),0.45*br*br)*a,a);
}
}
return acc;
}
float starField(vec2 p,float k,float t,float px){
vec2 g=p*k;
vec2 id=floor(g);
vec2 f=g-id-0.5;
float r=h21(id+3.1);
vec2 o=(h22(id+9.7)-0.5)*0.7;
float d=length(f-o)/k;
float s=sat((r-0.78)/0.22);
float tw=0.62+0.38*sin(mod(t*(0.4+1.3*h21(id+1.3)),TAU)+r*40.0);
float rad=max(0.9*px,0.0009+0.0015*s*s);
return step(0.78,r)*(exp(-d*d/(rad*rad))+0.22*s*s*exp(-d*d/(rad*rad*12.0)))*tw*(0.25+0.75*s);
}
vec4 sceneMain(vec2 xy){
vec2 uv=xy/uSize;
float asp=uSize.x/uSize.y;
vec2 p=vec2(uv.x*asp,uv.y);
float px=1.0/uSize.y;
float t=uTime;
float soft=sat(uSoft);
float calm=1.0-0.38*soft;
float fl=uFlow*2.0;
float rain=sat(uRain);
float wind=sat(uWind);
float lvl=sat(uLevel);
float seedA=fract(uSeed*0.1031+0.11);
float nt=day4f(0.0,0.0,0.0,1.0);
vec3 skyTop=day4(hx(86.0,108.0,166.0),hx(40.0,112.0,174.0),hx(50.0,46.0,108.0),hx(6.0,10.0,32.0));
vec3 skyMid=day4(hx(132.0,124.0,174.0),hx(66.0,150.0,194.0),hx(104.0,66.0,134.0),hx(13.0,22.0,58.0));
vec3 rosNear=day4(hx(198.0,140.0,158.0),hx(96.0,174.0,200.0),hx(214.0,108.0,122.0),hx(26.0,42.0,92.0));
vec3 rosFar=day4(hx(166.0,130.0,170.0),hx(92.0,170.0,200.0),hx(160.0,86.0,136.0),hx(22.0,36.0,84.0));
vec3 lowNear=day4(hx(250.0,206.0,184.0),hx(188.0,230.0,220.0),hx(255.0,180.0,112.0),hx(50.0,70.0,122.0));
vec3 lowFar=day4(hx(208.0,162.0,180.0),hx(170.0,218.0,216.0),hx(232.0,124.0,126.0),hx(40.0,58.0,108.0));
vec3 sunCol=day4(hx(255.0,222.0,190.0),hx(255.0,252.0,240.0),hx(255.0,178.0,108.0),hx(255.0,130.0,96.0));
vec3 haloCol=day4(hx(255.0,168.0,170.0),hx(200.0,240.0,235.0),hx(255.0,104.0,110.0),hx(120.0,80.0,140.0));
vec3 moonCol=hx(222.0,232.0,255.0);
vec3 seaFar=day4(hx(170.0,146.0,178.0),hx(60.0,146.0,182.0),hx(150.0,90.0,128.0),hx(24.0,38.0,82.0));
vec3 seaNear=day4(hx(70.0,82.0,132.0),hx(28.0,142.0,146.0),hx(40.0,30.0,80.0),hx(4.0,8.0,26.0));
vec3 isleCol=day4(hx(104.0,100.0,146.0),hx(56.0,112.0,138.0),hx(66.0,44.0,98.0),hx(7.0,11.0,30.0));
vec2 sunP=vec2(day4f(0.39,0.50,0.445,0.47),day4f(HZ-0.042,-0.30,HZ-0.050,HZ+0.16));
sunP.x=mix(sunP.x,0.50,smoothstep(0.42,0.18,sunP.y))*asp;
float sunHigh=smoothstep(0.36,0.12,sunP.y);
float sunR=day4f(0.034,0.030,0.040,0.040);
float sunE=HZ-sunP.y;
float sunUp=smoothstep(-0.07,0.012,sunE);
float twi=smoothstep(-0.24,-0.03,sunE)*(1.0-sunUp);
float glowAmt=day4f(1.0,0.55,1.0,0.6)*sunUp*(1.0-0.45*sunHigh)*(1.0+0.12*lvl)*(1.0-0.6*rain);
vec2 moonP=vec2(0.440*asp,day4f(0.36,0.50,0.46,0.488));
float moonR=0.026;
float sunI=sunUp*day4f(1.0,0.85,1.0,0.0)*(1.0-0.7*rain);
float moonI=nt*(1.0-0.6*rain);
float lamps=day4f(0.10,0.0,0.22,1.0);
float wA=day4f(0.040,0.18,0.046,0.034);
float wB=day4f(0.68,1.20,0.76,0.60)*(0.86+0.34*wind);
float warmX=exp(-sq((p.x-sunP.x)/0.62))*sunUp;
vec3 skyRos=mix(rosFar,rosNear,warmX);
vec3 skyLow=mix(lowFar,lowNear,warmX);
vec3 hazeCol=mix(skyLow,sunCol,0.20*sunUp*warmX);
float eH=HZ-uv.y;
vec2 ds=p-sunP;
float sdw=length(ds*vec2(0.55,1.0));
float ix2=p.x*0.9+uFlow*0.072+21.854+0.2*sin(seedA*TAU);
float mist=0.0;
if(abs(eH)<0.20){
float bank=fbm3(vec2(p.x*1.7+t*0.010+uFlow*0.10,2.0+t*0.004));
mist=(eH>0.0?exp(-eH/(0.008+0.034*bank*bank)):exp(eH*22.0))*(0.55+0.45*bank)*(day4f(0.62,0.36,0.34,0.30)+0.3*rain);
}
vec3 col;
vec2 gW=vec2(0.0);
float shim=0.5;
if(uv.y<HZ){
col=skyGrad(eH,skyTop,skyMid,skyRos,skyLow);
float mdisc=0.0;
if(nt>0.01){
float up=smoothstep(HZ-0.03,0.16,uv.y);
vec2 sp=p+vec2(uFlow*0.003,0.0);
float st=starField(sp,24.0,t,px)+0.6*starField(sp*1.7+3.3,31.0,t,px);
vec2 dm=p-moonP;
float md=length(dm);
col+=vec3(0.86,0.90,1.0)*st*up*nt*calm*smoothstep(0.03,0.15,md);
col+=moonCol*(0.32*exp(-md*26.0)+0.16*exp(-md*7.0)+0.06*exp(-md*2.4))*nt;
if(md<moonR+2.0*px){
float tex=fbm3(dm/moonR*1.7+4.0);
float disc=smoothstep(moonR+px,moonR-px,md);
vec3 mc=mix(vec3(0.74,0.78,0.90),vec3(1.0,0.98,0.93),smoothstep(0.30,0.72,tex));
col=mix(col,mc*1.15,disc*nt);
mdisc=disc*nt;
}
}
float sd=length(ds);
float sdisc=smoothstep(sunR+1.5*px,sunR-1.5*px,sd);
vec3 core=mix(sunCol*1.25,vec3(1.75,1.60,1.35),smoothstep(1.0,0.25,sd/sunR));
col=mix(col,core,sdisc*sunUp*(1.0-sunHigh));
float cdrift=t*0.008+uFlow*0.012+seedA*40.0-4.824;
vec3 cDark=day4(hx(136.0,120.0,168.0),hx(206.0,232.0,238.0),hx(108.0,74.0,136.0),hx(18.0,24.0,60.0));
vec3 cLit=day4(hx(236.0,178.0,178.0),hx(232.0,246.0,248.0),hx(255.0,150.0,116.0),hx(66.0,82.0,132.0));
float cTop=day4f(0.345,0.345,0.30,0.30);
if(p.y>cTop-0.005){
vec2 cp=vec2(p.x*1.55+cdrift,p.y*17.0);
float cw1=fbm3(cp*vec2(0.45,0.22)+vec2(7.0,0.0));
vec2 cq=cp+vec2(cw1*2.2,cw1*2.4);
float cl=fbm(cq);
float big=fbm3(vec2(p.x*0.9+cdrift*0.7+3.0,p.y*4.0));
float cband=smoothstep(HZ-0.004,HZ-0.040,p.y)*smoothstep(cTop,cTop+0.17,p.y);
float cth=0.655-0.24*big-0.12*rain;
float cm=smoothstep(cth,cth+0.13,cl)*cband;
float fib=fbm3(vec2(cq.x*3.0+5.0,cq.y*5.0));
cm*=0.62+0.38*smoothstep(0.30,0.66,fib);
float cl2=fbm(cq+vec2(0.02,0.50));
float nearSun=exp(-sdw*3.2)*sunUp;
float lit=sat((cl-cl2)*5.0+0.40+0.5*nearSun);
vec3 cc=mix(cDark,cLit,lit)+sunCol*nearSun*day4f(0.20,0.35,0.35,0.35);
col=mix(col,cc,cm*day4f(0.50,0.40,0.80,0.50)*(1.0-0.6*sdisc)*(1.0-0.85*mdisc)*(1.0-0.25*soft));
}
vec2 hp=vec2(p.x*1.2+cdrift*0.6+11.0,p.y*3.4-p.x*0.5);
float ci=fbm3(hp+fbm3(hp*0.7)*0.8);
col=mix(col,mix(cLit,skyRos,0.3),smoothstep(0.45,0.85,ci)*smoothstep(0.50,0.10,p.y)*0.12*calm);
}else{
float s=uv.y-HZ;
float zi=s+0.05;
vec2 w=vec2((p.x-0.5*asp)/zi,1.0/zi);
float ppu=zi*zi/px;
float dof=smoothstep(0.13,0.36,s);
float wnd=vnp(flowed(w,vec2(0.22,0.45),vec2(-0.07,0.02),fl,t,64.0),vec2(64.0,64.0))*0.62
+vnp(flowed(w,vec2(0.60,1.10),vec2(-0.16,0.0),fl,t,64.0)+vec2(0.0,3.0),vec2(64.0,64.0))*0.38;
float gs=smoothstep(0.40,0.70,wnd);
float slick=smoothstep(0.52,0.74,vnp(flowed(w,vec2(0.07,0.60),vec2(0.008,-0.012),fl,t,64.0),vec2(64.0,64.0)))*(1.0-0.6*wind)*(1.0-rain);
float ruf=sat(0.20+0.40*wind+gs*(0.25+0.75*wind)+0.3*rain)*(1.0-0.75*slick);
float warp=vnp(flowed(w,vec2(0.35,0.90),vec2(0.0,0.03),fl,t,64.0)+vec2(3.0,0.0),vec2(64.0,64.0))-0.5;
float grp=0.30+0.80*vnp(flowed(w,vec2(0.16,0.30),vec2(0.0,-0.02),fl,t,64.0)+vec2(9.0,0.0),vec2(64.0,64.0));
float phA=(w.y*0.995-w.x*0.10)*6.283-mod(fl*0.6283,TAU)+mod(t*0.50,TAU)+warp*6.0;
float phB=(w.y*0.940+w.x*0.34)*10.13+mod(fl*3.444,TAU)+mod(t*0.64,TAU)-warp*6.0+1.7;
float phC=(w.y*0.900-w.x*0.44)*16.98-mod(fl*7.471,TAU)+mod(t*0.82,TAU)+warp*8.0+4.1;
float cA=0.5+0.5*sin(phA);
float cB=0.5+0.5*sin(phB);
float cC=0.5+0.5*sin(phC);
float fA=smoothstep(2.5,7.0,1.00*ppu);
float fB=smoothstep(2.5,7.0,0.62*ppu);
float fC=smoothstep(2.5,7.0,0.37*ppu)*(1.0-0.6*dof)*(0.35+0.65*gs);
float swell=(0.52*cA*cA*fA+0.32*cB*cB*fB+0.16*cC*cC*fC)*grp;
vec2 gsw=(0.52*cA*cos(phA)*fA*vec2(-0.10,0.995)+0.32*cB*cos(phB)*fB*vec2(0.34,0.94)+0.16*cC*cos(phC)*fC*vec2(-0.44,0.90))*grp;
vec3 n1a=vndp(flowed(w,vec2(3.0,6.0),vec2(0.25,0.42),fl,t,256.0),vec2(256.0,256.0));
vec3 n1b=vndp(flowed(w,vec2(3.39,6.78),vec2(-0.19,0.31),fl,t,256.0)+vec2(31.0,0.0),vec2(256.0,256.0));
float f1=smoothstep(2.0,5.5,ppu/6.0)*(1.0-0.80*dof);
vec3 n2a=vndp(flowed(w,vec2(8.0,17.0),vec2(0.45,0.80),fl,t,256.0),vec2(256.0,256.0));
vec3 n2b=vndp(flowed(w,vec2(9.68,20.57),vec2(-0.36,0.55),fl,t,256.0)+vec2(57.0,0.0),vec2(256.0,256.0));
float f2=smoothstep(2.0,5.5,ppu/17.0)*(1.0-0.95*dof);
vec2 grip=(n1a.yz+n1b.yz)*f1*0.55+(n2a.yz+n2b.yz)*f2*0.45;
float rv=log(1.0+s*38.0)*13.0;
float st1=vnp(flowed(vec2(w.x,rv),vec2(3.0,1.0),vec2(0.0,-0.35),fl,t,256.0),vec2(256.0,256.0));
float st2=vnp(flowed(vec2(w.x,rv),vec2(7.0,1.9),vec2(0.0,0.22),fl,t,256.0)+vec2(9.0,0.0),vec2(256.0,256.0));
float farK=smoothstep(0.14,0.03,s);
vec2 gfar=vec2(st2-0.5,st1-0.5)*2.0*farK;
shim=st1;
vec2 g=gsw*1.0+grip*0.55*ruf+gfar*0.30*(0.35+0.65*gs)*(1.0-0.6*slick);
g*=calm;
gW=g;
float tilt=g.y;
float er=max(s*0.80+tilt*0.060+0.004,0.0);
vec3 refl=skyGrad(er,skyTop,skyMid,skyRos,skyLow);
float cosv=sat(0.02+s*0.75+tilt*0.30);
float fres=0.04+0.96*pow(1.0-cosv,5.0);
vec3 body=mix(seaFar,seaNear,smoothstep(0.0,0.36,s));
col=mix(body,refl*0.88,sat(fres+0.22*slick));
col=mix(col,seaFar,0.30*exp(-s*14.0));
float sw=gsw.y*calm;
col=mix(col,mix(skyRos,skyLow,0.5)*0.95,sat(-sw*1.8)*(0.10+0.22*dof));
col*=1.0-sat(sw*1.8)*(0.10+0.20*dof);
col*=1.0-0.16*gs*(0.25+0.75*wind)*(1.0-dof);
if(s<0.05){
float hr2=isle(ix2+g.x*0.010,11.0)*0.027-0.003;
float rm=sat((hr2-s*1.15)/(px*(1.5+s*260.0))+0.5);
col=mix(col,mix(hazeCol,isleCol,0.60),rm*0.42*(1.0-s/0.05));
}
col=mix(col,hazeCol,mist);
float le=max(sunE,0.0);
float jag=g.x*(0.3+s*4.0)*0.10;
float dxs=(p.x-sunP.x+jag)/(wA+wB*s);
float latS=exp(-dxs*dxs);
float dxm=(p.x-moonP.x+jag)/(0.045+0.55*(0.86+0.34*wind)*s);
float latM=exp(-dxm*dxm);
float along=mix(day4f(1.0,0.45,1.0,1.0),day4f(0.60,0.90,0.60,0.55),smoothstep(0.0,0.40,s));
float face=smoothstep(0.30,-0.35,tilt);
vec2 rdS=vec2(p.x-sunP.x+jag,s*0.36+tilt*0.05-le);
float colS=smoothstep(sunR*1.5,sunR*0.4,length(rdS*vec2(1.0,0.8)))*exp(-s*6.0);
vec2 rdM=vec2(p.x-moonP.x+jag,s*0.36+tilt*0.05-0.05);
float colM=smoothstep(moonR*1.6,moonR*0.4,length(rdM*vec2(1.0,0.7)))*exp(-s*6.0);
float lit2=latS*sunI+latM*moonI;
float dens=lit2*along*(0.45+0.55*face)*(0.62+0.38*gs);
dens=max(dens,(0.008+0.030*wind*gs)*ruf*day4f(0.5,1.0,0.4,0.25))*calm;
dens=sat(dens+0.45*(colS*sunI+colM*moonI));
vec2 q=vec2(p.x,s);
float bend=(st1-0.5)*0.8+swell*0.6;
float edge=1.3*px+0.09*max(s-0.15,0.0);
vec2 gl=glitter(q,max(0.0018,2.2*px),0.022,1.0,dens,bend,edge,t);
gl+=glitter(q,max(0.0025,3.0*px),0.032,2.0,dens,bend*0.7,edge,t);
gl+=glitter(q,max(0.0035,4.2*px),0.045,3.0,dens*0.9,bend*0.5,edge,t);
gl+=glitter(q+vec2(0.007,0.0),max(0.0021,2.6*px),0.012,6.0,dens*1.15,bend*0.9,edge,t)*0.85;
float sp=sparkle(q,max(0.0060,7.0*px),0.090,4.0,dens,t,px)+sparkle(q+vec2(0.021,0.0),max(0.0045,5.5*px),0.060,5.0,dens,t,px);
float foc=1.0-smoothstep(0.15,0.32,s);
float pop=0.35+0.65*smoothstep(0.0,0.45,dens);
float mixL=(latS*sunI+0.0001)/(lit2+0.0002);
vec3 hot=mix(moonCol,mix(sunCol,vec3(1.0,0.97,0.92),0.55),mixL);
vec3 warm=mix(moonCol*vec3(0.75,0.85,1.0),sunCol,mixL);
float glowBody=lit2*along*(0.14+0.20*face);
col+=warm*glowBody;
col+=hot*gl.x*1.25*foc*pop*(1.0+0.3*lvl)*(1.0-0.25*soft);
col+=warm*gl.y*0.30*foc*pop;
col+=hot*sp*1.6*(0.3+0.7*foc)*calm*(1.0+0.3*lvl);
if(s>0.11){
float lxB=mix(moonP.x,sunP.x,sat(sunI));
float wAB=mix(0.045,wA,sat(sunI));
float wBB=mix(0.55,wB,sat(sunI));
vec3 bA=mix(vec3(0.78,0.86,1.0),day4(vec3(1.0,0.80,0.68),vec3(1.0,1.0,1.0),vec3(1.0,0.70,0.34),vec3(1.0,0.70,0.34)),sat(sunI));
vec3 bB=mix(vec3(0.50,0.68,1.0),day4(vec3(1.0,0.58,0.66),vec3(0.72,1.0,0.93),vec3(1.0,0.40,0.22),vec3(1.0,0.40,0.22)),sat(sunI));
float bAmt=(sunI+moonI)*calm*1.25;
vec4 bk=bokeh(p,0.075,1.0,lxB,wAB,wBB,bAmt,bA,bB,t,px)
+bokeh(p+vec2(0.031,0.017),0.050,2.0,lxB,wAB,wBB,bAmt,bA,bB,t,px)
+bokeh(p+vec2(0.011,0.007),0.032,3.0,lxB,wAB,wBB,bAmt,bA,bB,t,px);
float bkK=day4f(1.0,0.62,1.0,1.0)*(1.0-0.30*soft);
col=col*(1.0-0.62*bkK*sat(bk.a*1.6))+bk.rgb*1.9*bkK*(1.0+0.3*lvl);
}
}
vec3 bloom=sunCol*(0.55*exp(-sdw*13.0)+day4f(0.20,0.30,0.30,0.30)*exp(-sdw*4.2))+haloCol*day4f(0.11,0.20,0.20,0.20)*exp(-sdw*1.9);
float rayK=0.0;
if(eH>0.0&&sdw>0.03&&sdw<0.62){
float ang=atan(ds.y,ds.x*0.55)/TAU;
float rays=vnp(vec2(ang*32.0,t*0.03),vec2(32.0,64.0))*0.65+vnp(vec2(ang*80.0+7.0,3.0-t*0.02),vec2(80.0,64.0))*0.35;
rayK=(smoothstep(0.30,0.75,rays)-0.45)*smoothstep(0.03,0.16,sdw)*smoothstep(0.62,0.18,sdw)*(1.0-sunHigh);
}
bloom*=glowAmt*(eH>0.0?1.0+0.30*rayK*(1.0-0.6*soft):0.45*exp(eH*9.0));
bloom+=mix(haloCol,sunCol,0.55)*twi*0.42*exp(-abs(eH)*(eH>0.0?8.0:16.0))*exp(-sq((p.x-sunP.x)/0.60))*(1.0-0.6*rain);
col=vec3(1.0)-(vec3(1.0)-min(col,1.0))*(vec3(1.0)-clamp(bloom,0.0,1.0))+max(col-1.0,0.0);
if(eH>-0.02&&eH<0.06){
float x1=p.x+uFlow*0.042+7.354+0.2*sin(seedA*TAU+1.0);
float h1=isle(x1,3.0)*0.016;
float a1=sat((h1-eH)/px+0.5)*step(0.0,eH);
col=mix(col,mix(hazeCol,isleCol,0.24+0.10*sat(eH/0.012)),a1);
float h2=isle(ix2,11.0)*0.027;
float e2=eH+0.003;
float a2=sat((h2-e2)/px+0.5)*sat(e2/px+0.5);
col=mix(col,mix(hazeCol,isleCol,0.50+0.22*sat(e2/0.02)),a2);
}
if(eH>0.0)col=mix(col,hazeCol,mist);
float lhx=mod(p.x-0.905*asp+uFlow*0.080+3.0,6.0)-3.0;
if(abs(lhx)<0.26&&eH>-0.07&&eH<0.06){
float e2=eH+0.003;
float rock=(0.0115*exp(-sq(lhx/0.028))+0.0060*exp(-sq((lhx+0.050)/0.045)))*(0.78+0.44*vn(vec2(lhx*60.0+40.0,3.0)));
float ar=sat((rock-e2)/px+0.5)*sat(e2/px+0.5);
col=mix(col,mix(hazeCol,isleCol,0.66),ar);
float tb=0.008;
float tt2=tb+0.024;
float tw=mix(0.0020,0.0012,sat((e2-tb)/0.024));
float tower=sat((tw-abs(lhx))/px+0.5)*sat((tt2-e2)/px+0.5)*step(tb,e2);
float cap=sat((0.0019-abs(lhx))/px+0.5)*sat((0.0022-abs(e2-tt2-0.0016))/px+0.5);
vec3 towerCol=mix(mix(hazeCol,isleCol,0.70),mix(hazeCol,vec3(1.0),0.55),day4f(0.70,1.0,0.25,0.0));
col=mix(col,towerCol,tower);
col=mix(col,mix(hazeCol,isleCol,0.78),cap);
float ph=mod(t*0.42,TAU);
float beam=0.30+0.70*pow(max(0.5+0.5*cos(ph),0.0),5.0);
vec2 ld=vec2(lhx,e2-tt2-0.0016);
float ldist=length(ld);
vec3 lampCol=hx(255.0,236.0,190.0);
float side=sin(ph);
float sx=ld.x*sign(side);
float ray=exp(-sq(ld.y/(px*1.3+abs(ld.x)*0.03)))*sat(1.0-sx/(0.03+0.16*abs(side)))*step(0.0,sx)*abs(side)*(0.6+0.4*cos(ph));
float lampG=glow(ldist,px*2.0)+0.45*beam*glow(ldist,px*7.0)+0.12*beam*glow(ldist,0.028);
float below=-eH-0.001;
float lrefl=step(0.0,below)*exp(-below*30.0)*exp(-sq((lhx+gW.x*0.004)/(px*2.4+below*0.06)))*(0.40+0.60*shim)*beam;
col+=lampCol*(lampG+0.10*max(ray,0.0)+0.55*lrefl)*lamps;
}
float bxc=mod((0.48+0.05*sin(seedA*TAU*2.0))*asp-uFlow*0.18+t*0.0011+0.35,asp+0.7)-0.35;
float bs=0.046;
float bwl=HZ+0.018;
if(abs(p.x-bxc)<bs*0.75&&uv.y>bwl-bs*1.1&&uv.y<bwl+bs*1.1){
float bob=sin(mod(t*0.70,TAU));
vec2 bq=vec2((p.x-bxc)/bs,(bwl-p.y)/bs+0.012*bob);
float lean=0.035*sin(mod(t*0.53,TAU)+1.0);
float be=1.1*px/bs;
float belly=0.025+0.06*wind;
vec3 hullCol=mix(isleCol*0.55,hazeCol,0.10);
vec3 sailCol=day4(hx(222.0,190.0,198.0),hx(255.0,255.0,250.0),hx(255.0,214.0,176.0),hx(64.0,78.0,124.0));
if(bq.y>-0.01){
vec2 bt=boat(bq+vec2(bq.y*lean,0.0),be,belly);
col=mix(col,sailCol*(0.88+0.12*sat(bq.y))*mix(1.0,0.90,smoothstep(-0.02,0.02,bq.x)),bt.y*0.96);
col=mix(col,hullCol,bt.x*step(-0.012,bq.y));
float ln=glow(length(bq-vec2(-0.10,0.10)),0.035)+0.3*glow(length(bq-vec2(-0.10,0.10)),0.14);
col+=hx(255.0,206.0,140.0)*ln*lamps;
}else{
vec2 rq=vec2(bq.x+(gW.x*0.22+(shim-0.5)*0.07)*sat(-bq.y*3.0),-bq.y*1.05);
vec2 bt=boat(rq,be*3.0,belly);
float fade=exp(bq.y*2.6);
col=mix(col,mix(sailCol,hullCol,0.25),bt.y*fade*0.30);
col=mix(col,hullCol,bt.x*fade*0.50);
}
}
if(lamps>0.01&&eH<0.02&&eH>-0.09){
for(int i=0;i<6;i++){
float fi=float(i);
float lx0=mod((0.05+0.50*sq(h11(fi*7.3+2.0))+0.012*fi)*asp-uFlow*(0.085+0.045*h11(fi*3.7)),asp+0.5)-0.25;
float ls=0.0015+0.0095*h11(fi*5.1+1.0);
vec2 lp=vec2(lx0,HZ+ls);
float d=length(p-lp+vec2(0.0,0.0012));
float hullA=sat((0.0036-abs(p.x-lx0))/px+0.5)*sat((0.0009-abs(p.y-lp.y))/px+0.5)*sat(lamps*4.0);
col=mix(col,mix(hazeCol,isleCol,0.78),hullA*0.85);
float tw=0.78+0.22*sin(mod(t*(0.7+0.5*h11(fi*2.2)),TAU)+fi*3.0);
vec3 lc=mix(hx(206.0,255.0,232.0),hx(255.0,214.0,150.0),step(0.55,h11(fi*4.4+0.3)));
float lb=0.80+0.30*h11(fi*6.1+0.9);
float core=glow(d,px*1.5)+0.35*glow(d,px*5.0)+0.10*glow(d,px*16.0);
float below=p.y-lp.y;
float refl=step(0.0,below)*exp(-below*46.0)*exp(-sq((p.x-lx0+gW.x*0.006)/(px*2.2+below*0.06)))*(0.20+0.80*shim*shim*1.6);
col+=lc*(core*tw*1.2+refl*0.75)*lamps*lb;
}
}
if(uv.y>0.36&&uv.y<0.61){
for(int i=0;i<4;i++){
float fi=float(i);
float pr=floor(fi*0.5);
float sec=fi-pr*2.0;
float per=78.0+46.0*h11(pr*3.3+0.7);
float dir=h11(pr*5.3+1.1)>0.5?1.0:-1.0;
float u=fract(t/per+h11(pr*9.1+seedA*5.0)-uFlow*0.012*dir-sec*(0.012+0.010*h11(pr*4.7)));
float xx=u/0.30;
float bx=dir>0.0?(-0.12+(asp+0.24)*xx):(asp+0.12-(asp+0.24)*xx);
float by=0.42+0.10*h11(pr*7.7+2.0)+sec*(0.030*h11(pr*6.1+3.0)-0.004)+0.018*sin(u*31.0+fi*2.0);
float sz=0.011+0.007*h11(fi*2.9+4.0);
float glide=smoothstep(0.35,0.65,vnp(vec2(t*0.21+fi*7.0,fi),vec2(256.0,8.0)));
float flap=sin(mod(t*(5.2+fi*0.5),TAU)+fi*2.0)*mix(1.0,0.18,glide);
vec2 gq=vec2((p.x-bx)*dir,by-p.y)/sz;
float ga=0.0;
if(abs(gq.x)<1.2&&abs(gq.y)<1.0)ga=gull(gq,flap,0.9*px/sz);
vec3 gc=mix(isleCol*0.75,vec3(0.96,0.95,0.93),day4f(0.10,0.16,0.05,0.0));
col=mix(col,gc,ga*day4f(0.80,0.85,0.85,0.35)*calm);
}
}
float lum=dot(col,vec3(0.299,0.587,0.114));
col=mix(col,vec3(lum)*vec3(0.90,0.96,1.02),0.40*rain)*(1.0-0.10*rain);
vec3 over=max(col-0.80,0.0);
col=min(col,0.80)+0.20*(1.0-exp(-over/0.20));
return finish(col,xy);
}
uniform float uDim;
uniform vec4 uVeil;
uniform float uFade;
void main() {
    vec4 c = sceneMain(vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y));
    gl_FragColor = vec4(mix(c.rgb, uVeil.rgb, uVeil.a) * (1.0 - uDim), uFade);
}
