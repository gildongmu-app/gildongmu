// reeds — tools/site/agsl2glsl.mjs 가 app/src/main/assets/scenes/_lib.agsl + reeds.agsl 에서 만든 파일. 손으로 고치지 말 것.
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
const float HORIZON=0.625;
const float PERSP=0.48;
const float CAP=0.544;
float lineA(float d,float w,float b){
float ad=abs(d);
return sat((min(ad+w,0.5*b)-max(ad-w,-0.5*b))/b);
}
vec2 stroke(vec2 p,vec2 o,float ang,float kap,float len){
vec2 a=vec2(sin(ang),-cos(ang));
vec2 r=p-o;
float s=dot(r,a)/len;
float sc=clamp(s,-0.2,1.3);
float d=dot(r,vec2(-a.y,a.x))/len-kap*sc*sc;
return vec2(s,d/sqrt(1.0+4.0*kap*kap*sc*sc));
}
vec2 vane(float s,float ad,float A,float N,float r,float b){
float x=ad/A;
float s0=s-x;
float kq=(s0+0.030*sin(s0*23.0+r*50.0))*N+r*7.0+0.30*sin(x*9.0+r*40.0+s0*6.0);
float id=floor(kq);
float f=fract(kq)-0.5;
float rs=h21(vec2(id+0.5,r*91.0));
float s0c=(id+0.5-r*7.0)/N;
float len=(0.66-0.46*sat(s0c))*(0.50+0.38*rs+0.22*step(0.80,rs));
float rho=x/len;
float sp=A/(N*sqrt(1.0+A*A));
float dist=abs(f)*sp;
float th=max((0.0016+0.0014*rs)*(1.0-0.6*sat(rho)),0.36*b);
float nz=0.5+0.25*sin(x*31.0+id*2.4+r*9.0)+0.25*sin(x*53.0+id*5.1);
float edge=1.0-4.0*f*f;
float lod=sat(sp/b*0.7-0.35);
float coreL=lineA(dist,th,b)*edge*(1.0-0.6*sat(rho));
float fw=2.6*th+0.5*b+0.12*sp;
float fz=exp(-dist*dist/(fw*fw))*edge;
float endF=smoothstep(-0.04,0.02,s0c)*smoothstep(0.70,0.60,s0c)*smoothstep(1.0,0.55,rho)*(0.35+0.65*smoothstep(0.0,0.5,nz+0.6-rho*0.6));
float cr=endF*mix(0.10,coreL,lod);
return vec2(sat(endF*mix(0.42,fz*(0.35+0.95*nz),lod)+cr*0.7),cr);
}
vec3 plume(vec2 sd,float r,float G,float b,float det,float opn){
float s=sd.x;
float ad=abs(sd.y);
float dn=step(0.0,sd.y);
float A=max(mix(0.19-0.17*G,0.33-0.14*G,dn)*opn,0.05)*(1.18-0.74*sat(s));
float x=ad/A;
float s0=s-x;
float lenM=(0.66-0.46*sat(s0))*0.72;
float body=smoothstep(-0.06-b,0.04+b,s0)*smoothstep(0.72+b,0.56-b,s0);
float fill=body*smoothstep(1.0+2.0*b,0.15,x/lenM);
float sa=0.75*body*smoothstep(1.05+2.0*b,0.40,x/lenM);
float cr=0.1*fill;
if(det>0.8){
vec2 v1=vane(s,ad,A,mix(8.0,13.0,dn),r,b);
vec2 v2=vane(s,ad,A*0.66,mix(6.0,9.0,dn),r+0.31,b);
vec2 v3=vane(s+0.05,ad,A*1.25,mix(4.0,6.0,dn),r+0.67,b);
sa=1.0-(1.0-v1.x)*(1.0-v2.x*0.9)*(1.0-v3.x*0.8);
cr=max(v1.y,max(v2.y,v3.y*0.8));
}
float axis=lineA(sd.y,0.0040*(1.0-0.9*sat(s)),b)*step(0.0,s)*smoothstep(0.62,0.34,s);
float a=1.0-(1.0-sa*0.92)*(1.0-fill*0.40)*(1.0-axis*0.6);
float tau=fill*(0.60+0.40*sa);
float lum=sat(1.0-0.95*tau-0.30*cr-0.3*axis);
float halo=smoothstep(-0.18,0.04,s0)*smoothstep(0.90,0.50,s0)*smoothstep(1.6,0.1,x/lenM);
return vec3(a,lum,halo);
}
vec4 reed(vec2 q,vec2 root,float H,float sc,vec3 r,float G,float wob,float b,float det,float ext){
float h=(root.y-q.y)/H;
float lean=(r.x-0.40)*0.08;
float bow=(r.y-0.35)*0.08+wob;
float hc=clamp(h,0.0,1.0);
float sl=lean+2.0*bow*hc;
float dS=(q.x-root.x-H*(lean*hc+bow*hc*hc))/sqrt(1.0+sl*sl);
float thick=sc*(1.0+6.5*step(det,0.3));
float dark=lineA(dS,thick*0.0018*(1.0-0.5*hc),b)*step(0.0,h)*sat((1.0-h)*H/b+0.5);
float li=clamp(floor(h/0.42-0.3),0.0,1.0);
float lr=fract(r.z*7.7+li*0.37);
float hA=0.42*(li+0.35+0.20*lr);
float lL=min(H*(0.20+0.12*lr),ext);
vec2 ld=stroke(q,vec2(root.x+H*(lean*hA+bow*hA*hA),root.y-H*hA),lean+0.15+0.25*lr+G*0.7+wob*3.0,0.12+0.2*lr+0.4*G,lL);
float ls=sat(ld.x);
float lf=lineA(ld.y*lL,thick*0.0034*pow(1.0-ls,0.65)*smoothstep(0.0,0.15,ls),b)*step(0.0,ld.x)*step(ld.x,1.0);
if(lf>dark)dark=2.0+lf;
float Lp=min(sc*(0.105+0.05*r.z),ext*1.15);
vec2 sd=stroke(q,vec2(root.x+H*(lean+bow),root.y-H),atan(lean+2.0*bow)+0.08+0.22*r.z+G*0.5+wob*3.0,(0.18+0.22*r.x)*(1.0-1.2*G)+0.1*G,Lp);
vec3 m=vec3(0.0);
if(sd.x>-0.10&&sd.x<1.12&&sd.y>-0.24&&sd.y<0.62)m=plume(sd,r.z,G,b/Lp,det,(0.80+0.50*fract(r.x*7.7))*(0.7+0.3*det));
return vec4(dark,m);
}
float windAt(float x,float t){
return smoothstep(0.25,0.75,vn(vec2(x-t*0.30,t*0.06)));
}
float topAt(float u,vec4 geo,vec4 zn){
return geo.y+(geo.z-geo.y)*smoothstep(zn.x,zn.y,u)+(geo.w-geo.y)*smoothstep(zn.z,zn.w,u);
}
vec3 furRow(vec2 p,float yT,float Lp,float seed,float b,float G){
float len=Lp/0.62;
float yB=yT+0.38*len;
if(p.y<yT-Lp*1.3||p.y>yB+len*0.35)return vec3(0.0);
float hh=clamp((yB+len*0.3-p.y)/len,0.0,2.2);
float hq=min(hh,1.0);
float cell=len*0.42;
float X=(p.x+mod(uFlow*4.0*Lp,cell*4096.0)-1.5*G*len*hq*(2.0*hh-hq))/cell+seed*3.7;
float ci=floor(X);
vec3 o=vec3(0.0);
for(int j=0;j<2;j++){
float id=ci+float(j)-1.0;
float r1=h21(vec2(mod(id,4096.0),seed));
float r2=fract(r1*37.3+0.21);
float L=len*(0.72+0.56*r2);
float up=yB+(r1-0.5)*len*0.6-p.y;
float s=up/L;
float sp=sat((s-0.36)/0.64);
float lean=(r2-0.1)*0.25+0.2*sin(uTime*(0.8+r1)+r2*30.0)*(0.1+G);
float d=((X-id-0.5-(r1-0.5)*0.5)*cell-lean*up)/L-(0.05+0.32*G)*sp*sp;
float w=0.014+0.13*sqrt(sp)*(1.0-sp);
float bb=b/L;
float e=abs(d)/(w+0.5*bb);
float a=smoothstep(1.0,0.45,e)*step(0.0,s)*smoothstep(1.0,0.92,s)*min(1.0,w/(bb+0.001)+0.3);
float isP=smoothstep(0.30,0.42,s);
float l=a*isP*(0.62+0.38*r1)*(0.66+0.34*sp)*(1.0-0.16*sat(w/bb*0.3-0.3)*(1.0+sin(d/w*7.0+r1*40.0)));
o=vec3(max(o.x,a*(1.0-isP)),max(o.y,l),mix(o.z,sat(0.10+0.55*sp+0.5*e*e),step(o.y,l)));
}
return o;
}
float seeds(vec2 p,float k,float sp,float seed,float px,float big){
float row=floor(p.y*k);
float rr=h21(vec2(row,seed));
float gx=(p.x-uTime*sp*(0.6+0.8*rr)+uFlow*(0.5+0.5*rr))*k+rr*17.0;
vec2 id=vec2(floor(gx),row);
float r1=h21(id+seed);
float r2=fract(r1*37.3+0.21);
vec2 c=vec2(0.3+0.4*r2,0.5+0.22*sin(uTime*(0.25+0.35*r2)+r1*40.0));
float d=length((vec2(fract(gx),fract(p.y*k))-c)/k);
float tw=0.5+0.5*sin(uTime*(0.5+1.1*r2)+r1*60.0);
float r0=(0.0010+0.0016*r2)*big;
float rad=max(r0,px*0.8);
return(glow(d,rad)+0.22*glow(d,rad*3.2))*(0.25+0.75*tw*tw)*sat(r1*2.6-1.2)*sq(r0/rad);
}
float ridge(float x,float seed){
return 0.50*vn(vec2(x,seed))+0.32*(1.0-abs(2.0*vn(vec2(x*2.3+3.1,seed+2.0))-1.0))+0.18*vn(vec2(x*5.3,seed+5.0));
}
vec4 sceneMain(vec2 xy){
vec2 uv=xy/uSize;
float asp=uSize.x/uSize.y;
vec2 p=vec2(uv.x*asp,uv.y);
float px=1.0/uSize.y;
float t=uTime;
float calm=1.0-0.35*uSoft;
float seedV=mod(uSeed,97.0);
float nt=day4f(0.0,0.0,0.0,1.0);
vec2 sunP=vec2(day4f(0.26,0.55,0.385,0.60)*asp,day4f(0.584,-0.40,0.576,0.84));
vec2 moonP=vec2(day4f(0.82,0.50,0.22,0.45)*asp,day4f(0.76,0.90,0.76,0.468));
float sunVis=smoothstep(0.36,0.52,sunP.y)*smoothstep(HORIZON+0.10,HORIZON-0.02,sunP.y);
float sunPow=sunVis*day4f(0.42,1.0,1.0,1.0)*(1.0-0.25*uSoft);
vec3 sunC=day4(hx(255.0,220.0,200.0),hx(255.0,246.0,226.0),hx(255.0,190.0,120.0),hx(255.0,150.0,110.0));
vec3 moonC=hx(206.0,220.0,255.0);
vec3 lightC=mix(sunC,moonC,nt);
vec2 sunG=vec2(sunP.x,min(sunP.y,HORIZON-0.03));
vec2 moonG=vec2(moonP.x,min(moonP.y,HORIZON-0.03));
vec3 skyTop=day4(hx(100.0,104.0,150.0),hx(54.0,106.0,178.0),hx(52.0,50.0,104.0),hx(8.0,12.0,34.0));
vec3 skyMid=day4(hx(154.0,126.0,158.0),hx(92.0,150.0,202.0),hx(132.0,86.0,134.0),hx(18.0,26.0,62.0));
vec3 skyLow=day4(hx(206.0,164.0,168.0),hx(142.0,186.0,216.0),hx(226.0,132.0,116.0),hx(40.0,54.0,100.0));
vec3 skyHor=day4(hx(244.0,220.0,204.0),hx(214.0,228.0,224.0),hx(255.0,184.0,124.0),hx(66.0,82.0,130.0));
float sy=sat((HORIZON-p.y)/HORIZON);
vec3 col=mix(skyHor,skyLow,smoothstep(0.0,0.11,sy));
col=mix(col,skyMid,smoothstep(0.04,0.30,sy));
col=mix(col,skyTop,smoothstep(0.26,0.96,sy));
float sd=length((p-sunG)*vec2(0.66,1.15));
col+=sunC*(0.24*exp(-sd*3.6)+0.22*exp(-sd*11.0))*sunPow*(1.0+0.3*uLevel);
col=mix(col,mix(sunC,vec3(1.0),0.62),smoothstep(0.036,0.031,length(p-sunP))*sunPow);
if(nt>0.02){
float st=stars(p+vec2(uFlow*0.003,0.0),24.0)+stars(p*1.7+3.3,31.0)*0.6;
col+=vec3(0.86,0.90,1.0)*st*smoothstep(0.56,0.05,p.y)*nt*0.75*calm;
float md=length(p-moonP);
col+=moonC*(0.26*exp(-md*6.0)+0.30*exp(-md*22.0))*nt;
col=mix(col,hx(250.0,246.0,236.0)*(0.74+0.22*vn(p*30.0+3.0)),smoothstep(0.036,0.033,md)*nt);
}
vec2 cp=vec2(p.x*0.75+t*0.008+uFlow*0.012+seedV,p.y*5.5);
float cl=fbm(cp+vec2(0.0,0.5*sin(cp.x*1.7+0.6*sin(cp.y*0.9))));
float lowC=0.12*smoothstep(0.38,0.56,p.y);
float cm=smoothstep(0.46-lowC,0.76-lowC,cl)*smoothstep(0.64,0.54,p.y)*smoothstep(0.06,0.42,p.y);
vec3 cCol=day4(hx(246.0,206.0,200.0),hx(188.0,214.0,232.0),hx(255.0,160.0,128.0),hx(52.0,62.0,110.0));
vec3 hillA=day4(hx(196.0,172.0,190.0),hx(136.0,170.0,198.0),hx(190.0,116.0,130.0),hx(34.0,44.0,86.0));
col=mix(col,mix(cCol+sunC*0.25*exp(-sd*3.0)*sunPow,mix(hillA,skyLow,0.30),smoothstep(0.40,0.54,p.y)),cm*day4f(0.50,0.50,0.62,0.40));
vec3 haze=mix(skyHor,lightC,0.30);
vec3 hillD=day4(hx(150.0,128.0,156.0),hx(90.0,122.0,154.0),hx(112.0,60.0,96.0),hx(15.0,21.0,48.0));
float dip=0.020*exp(-sq((p.x-sunP.x)/0.30))*sunVis;
float ra=HORIZON-0.010-0.095*ridge((p.x+uFlow*0.012)*0.9+seedV*0.7,1.0)+dip;
col=mix(col,mix(hillA,haze,0.75*smoothstep(0.0,0.075,p.y-ra)),smoothstep(-1.5,1.5,(p.y-ra)/px));
float rb=HORIZON-0.006-0.066*ridge((p.x+uFlow*0.020)*1.5+seedV*1.3+5.0,4.0)+0.6*dip;
col=mix(col,mix(mix(hillA,hillD,0.55),haze,0.72*smoothstep(0.0,0.055,p.y-rb)),smoothstep(-1.2,1.2,(p.y-rb)/px));
float hxd=(p.x+uFlow*0.032)*2.4+seedV*2.1+9.0;
float rd=HORIZON-0.002-0.040*ridge(hxd,7.0)-0.0030*vn(vec2(hxd*22.0,2.0));
col=mix(col,mix(hillD,haze,0.66*smoothstep(0.0,0.034,p.y-rd)),sat((p.y-rd)/px+0.5));
float nearS=exp(-length((p-sunG)*vec2(1.0,0.55))*2.4)*sunVis*day4f(0.62,1.0,1.0,1.0);
float nearM=exp(-length((p-moonG)*vec2(1.0,0.55))*2.4)*nt;
float nearL=nearS+nearM+day4f(0.0,0.20,0.0,0.0)*smoothstep(-0.2,1.1,uv.x);
float amb=day4f(0.86,1.0,0.80,0.72)*(1.0+0.15*uLevel);
float Ly=PERSP*max(p.y-HORIZON,0.008);
float G0=0.02+0.06*uWind;
float G1=0.13+0.11*uWind;
vec3 fieldC=day4(hx(124.0,100.0,112.0),hx(178.0,158.0,118.0),hx(70.0,34.0,48.0),hx(10.0,14.0,34.0));
vec3 stemC=day4(hx(198.0,166.0,166.0),hx(192.0,164.0,112.0),hx(128.0,70.0,62.0),hx(30.0,40.0,78.0));
vec3 coreA=day4(hx(222.0,190.0,186.0),hx(240.0,228.0,196.0),hx(190.0,112.0,104.0),hx(74.0,90.0,144.0));
vec3 coreS=day4(hx(242.0,206.0,190.0),hx(246.0,232.0,196.0),hx(248.0,160.0,88.0),hx(124.0,144.0,202.0));
vec3 rimA=day4(hx(255.0,240.0,232.0),hx(255.0,253.0,244.0),hx(255.0,214.0,190.0),hx(150.0,172.0,228.0));
vec3 rimS=day4(hx(255.0,246.0,236.0),hx(255.0,254.0,246.0),hx(255.0,246.0,210.0),hx(230.0,238.0,255.0));
vec3 darkN=day4(hx(136.0,114.0,128.0),hx(122.0,102.0,70.0),hx(104.0,54.0,52.0),hx(9.0,12.0,30.0));
float wS=sat(nearL*1.7);
vec3 coreC=mix(coreA,coreS,wS)*(amb+0.30*nearL);
vec3 rimC=mix(rimA,rimS,wS)*(0.92+0.08*amb+0.60*nearL);
vec3 stemL=mix(stemC,coreS,0.5*wS)*(amb+0.3*nearL);
vec3 haloC=lightC*(0.05+0.32*nearL);
float tex=vn(vec2((p.x-0.5*asp)*6.0/Ly+uFlow*24.0,log(Ly)*14.0));
float mist=day4f(0.55,0.06,0.10,0.14)*(0.5+vn(vec2(p.x*1.2+t*0.012+uFlow*0.2,p.y*7.0)));
float dapple=0.80+0.40*vn(vec2((p.x-0.5*asp)*0.5/Ly+uFlow*2.0+t*0.02,log(Ly)*2.2+5.0));
if(p.y>HORIZON-0.02){
float fd=sat((p.y-HORIZON)/(1.0-HORIZON));
col=mix(col,mix(mix(fieldC,haze,0.70),fieldC,smoothstep(0.0,0.22,fd)),sat((p.y-HORIZON)/px+0.5));
for(int i=0;i<12;i++){
float Lp=0.0039*pow(1.26,float(i));
float hzA=sat((0.40+0.5*mist)*exp(-Lp*30.0));
float g=windAt((p.x-0.5*asp)*min(4.5,0.19/(1.4*Lp+0.02))+0.6*log(Lp*100.0),t);
vec3 fr=furRow(p,HORIZON+Lp/PERSP,Lp,float(i)*5.3+1.0+seedV,px*1.2,G0+G1*pow(g,1.2));
col=mix(col,mix(fieldC,haze,hzA),fr.x*0.75);
col=mix(col,mix(mix(coreC,rimC,fr.z)*(0.80+0.50*g)*dapple,haze,hzA),fr.y*0.95);
}
}
for(int i=0;i<15;i++){
float fi=float(i);
float k=max(9.0-fi,0.0);
float Lp=0.17/(1.0+0.25*k);
float yT=HORIZON+Lp/PERSP;
float hzA=exp(-Lp*16.0);
vec4 geo=vec4(yT+2.6*Lp+0.04,yT,yT,yT);
vec4 par=vec4(0.90*Lp,Lp/0.13,4.0*Lp,10.0+3.7*k);
vec4 zn=vec4(0.22,-0.02,0.60,0.82);
float Href=2.6*Lp+0.04;
vec2 lever=vec2(yT+1.4*Lp+0.02,1.4*Lp+0.02);
float wx=(p.x-0.5*asp)*min(4.5,0.19/(1.4*Lp+0.02))+0.6*log(Lp*100.0);
float blur=px*(1.2+0.6*hzA+0.6*step(k,0.5));
float tkA=0.66;
float det=0.6+0.4*step(24.0*px,Lp);
float side=0.0;
vec3 dk=mix(stemL,haze,0.30*hzA);
if(fi>9.5){
geo=vec4(1.13+0.02*(fi-10.0),1.25,CAP,CAP);
par=vec4(0.21+0.016*(fi-10.0),1.55,1.3,51.0+7.1*(fi-10.0));
zn=vec4(0.24,0.07,0.58,0.82);
Href=0.40;
lever=vec2(geo.x,0.64);
wx=p.x*0.35+7.0;
blur=px*1.3;
det=1.6;
tkA=0.0;
hzA=0.0;
side=1.0;
dk=darkN*(amb+0.3*nearL);
}
if(fi>12.5){
geo=vec4(1.40,2.20,CAP+0.03,CAP+0.03);
lever=vec2(1.40,0.64);
par=vec4(0.26+0.08*(fi-13.0),1.5,2.2+0.6*(fi-13.0),77.0+9.3*(fi-13.0));
zn=vec4(0.20,0.02,0.80,0.98);
blur=px*(16.0+8.0*(fi-13.0));
det=0.0;
dk=darkN*0.55;
}
float u=p.x/asp;
float top=min(topAt(u,geo,zn),topAt(u-1.6*par.x/asp,geo,zn));
float LpR=par.y*0.13;
if(p.y>top-(1.0-side)*LpR*1.6-0.02&&p.y<geo.x+0.06){
float g=windAt(wx,t);
float G=G0+G1*pow(g,1.2);
float hL=clamp((lever.x-p.y)/lever.y,0.0,2.6);
float hq=min(hL,1.0);
float bend=G*lever.y*hq*(2.0*hL-hq);
float flow=mod(uFlow*par.z,par.x*4096.0);
vec2 q=vec2(p.x+flow-bend+par.w*3.1,p.y-0.35*G*bend*hq);
col=mix(col,haze,sat(mist*0.30*smoothstep(0.5,5.0,k)));
col=mix(col,dk*(0.58+0.12*tex),sat(tkA*smoothstep(yT+0.2*Lp,yT+2.4*Lp,p.y)*(0.90+0.2*tex)));
float sheen=mix((0.80+0.50*g)*dapple,0.88+0.14*g,side);
float ci=floor(q.x/par.x);
for(int j=0;j<2;j++){
float id=ci+float(j)-1.0;
float idw=mod(id,4096.0);
float r1=h21(vec2(idw,par.w+seedV));
vec3 r=vec3(r1,fract(r1*37.3+0.21),fract(r1*91.7+0.63));
float rootX=(id+0.5+(r.x-0.5)*0.2)*par.x;
float yr=geo.x+r.y*0.02;
float clump=0.5+0.5*sin(idw*0.83+par.w)*sin(idw*0.31+par.w*1.7);
float sci=par.y*(0.72+0.26*clump+0.30*r.y);
float capY=topAt((rootX-par.w*3.1-flow)/asp,geo,zn);
float H=max(mix((yr-capY)*(0.68+0.22*clump+0.18*fract(r1*173.1)),(yr-capY-1.2*sci*(0.105+0.05*r.z))*(0.86+0.14*fract(r1*173.1)),side),0.002);
float grown=smoothstep(0.35,0.80,H/Href);
float wob=(0.006+0.05*G)*sin(t*(0.9+1.3*r.y)+r.x*40.0)+0.12*G*r.z+0.10*step(det,0.3);
vec4 m=reed(q,vec2(rootX,yr),H,sci*(0.5+0.5*grown),r,G,wob,blur,det,par.x)*step(0.12*(1.0-side),fract(r1*57.3));
float isLf=step(1.5,m.x);
col=mix(col,mix(dk,coreC*0.9,isLf*(0.12+0.40*side))*(0.85+0.3*r.z),m.x-2.0*isLf);
float pv=smoothstep(0.30,0.42,H/Href)*(0.55+0.45*step(0.5,det));
col=mix(col,mix(mix(mix(coreC,coreS*0.95,0.5*side),rimC,m.z),haze,0.20*hzA)*sheen*(0.90+0.18*r.z),m.y*pv);
col+=haloC*m.w*sheen*(0.5+0.5*side)*pv;
}
}
}
col=mix(col,mix(stemL*0.72,coreC,0.42),0.36*uSoft*smoothstep(HORIZON-0.01,HORIZON+0.03,p.y));
col+=sunC*(0.14*exp(-sd*4.0)+0.10*exp(-sd*12.0)+0.07*exp(-abs(p.y-sunG.y)*60.0-abs(p.x-sunG.x)*1.6))*sunPow*(1.0+0.3*uLevel);
col+=moonC*0.10*exp(-length(p-moonG)*7.0)*nt*(1.0+0.3*uLevel);
float sdn=(seeds(p,13.0,0.030,3.0,px,1.0)+seeds(p+7.7,7.0,0.045,9.0,px,2.0)*0.8+seeds(p+3.1,3.4,0.070,5.0,px,7.0)*0.22)*smoothstep(0.50,0.68,p.y-0.06*exp(-sd*5.0)*sunVis);
col+=mix(lightC,vec3(1.0),0.4)*sdn*(0.22+1.8*nearL)*calm;
return finish(col,xy);
}
uniform float uDim;
uniform vec4 uVeil;
uniform float uFade;
void main() {
    vec4 c = sceneMain(vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y));
    gl_FragColor = vec4(mix(c.rgb, uVeil.rgb, uVeil.a) * (1.0 - uDim), uFade);
}
