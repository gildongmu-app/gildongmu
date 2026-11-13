// snow — tools/site/agsl2glsl.mjs 가 app/src/main/assets/scenes/_lib.agsl + snow.agsl 에서 만든 파일. 손으로 고치지 말 것.
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
const float TAU=6.2831853;
const float LAMP_H=0.112;
float vnp(vec2 p){
vec2 i=floor(p);
vec2 f=p-i;
vec2 u=f*f*(3.0-2.0*f);
vec2 i0=mod(i,256.0);
vec2 i1=mod(i+1.0,256.0);
return mix(mix(h21(i0),h21(vec2(i1.x,i0.y)),u.x),mix(h21(vec2(i0.x,i1.y)),h21(i1),u.x),u.y);
}
float fbp3(vec2 p){
return(0.5*vnp(p)+0.25*vnp(p*2.0+vec2(13.7,7.1))+0.125*vnp(p*4.0+vec2(31.1,17.3)))/0.875;
}
float fbp(vec2 p){
return fbp3(p)*0.875+0.0625*vnp(p*8.0+vec2(5.3,41.9))+0.03125*vnp(p*16.0+vec2(71.3,23.7));
}
float bigGust(float t){
return smoothstep(0.52,0.97,0.5+0.5*sin(t*0.19+1.7*sin(t*0.061+mod(uSeed,61.0))));
}
float snowLayer(vec2 p,float fi,float t,float px){
float z=fi/6.0;
float S=0.030*pow(1.55,fi);
float v=0.042*pow(1.32,fi);
float rad=0.00108*pow(1.72,fi);
float soft=max(px*0.7,(0.7+0.42*fi*fi)/1208.0);
float slant=0.30+0.95*uWind;
float ph=fi*1.7+mod(uSeed,61.0);
float wx=p.x*0.55+p.y*0.25;
float wamp=(0.5+1.5*uWind)*(0.55+0.75*z)*(1.0+0.7*bigGust(t-(uSize.x/uSize.y-p.x)*0.8));
vec2 w=p;
w.x+=wamp*(0.060*sin(0.47*t+wx*0.8+ph)+0.035*sin(0.83*t+wx*1.2+ph*2.3)+0.020*sin(1.31*t+wx*1.7+ph*0.7));
w.y+=wamp*(0.022*sin(0.61*t+wx*1.1+ph*1.3)+0.012*sin(1.07*t-wx*1.5+ph));
float gx=(w.x+slant*w.y+uFlow*(0.20+0.18*fi))/S+fi*7.31+mod(uSeed,61.0)*3.0;
float ix=floor(gx);
float sdx=(gx-ix)<0.5?-1.0:1.0;
vec2 vd=normalize(vec2(-slant,1.0));
float occ=mix(0.86,0.26,z);
float acc=0.0;
for(int a=0;a<2;a++){
float cxi=ix+float(a)*sdx;
float cw=mod(cxi,61.0);
float hc=h11(cw+fi*13.0);
float vc=v*(0.80+0.40*hc);
float gy=(w.y-t*vc)/S+hc*19.0;
float iy=floor(gy);
float sdy=(gy-iy)<0.5?-1.0:1.0;
for(int b=0;b<2;b++){
float cyi=iy+float(b)*sdy;
vec2 r1=h22(vec2(cw,mod(cyi,53.0))+fi*17.0);
float r3=fract(r1.x*17.3+r1.y*5.1);
float trel=(19.0*hc-cyi-0.5)*S/vc;
float ex=smoothstep(-0.04,0.04,mix(0.50,1.0,bigGust(trel-cw*S*0.7))*occ-r3);
float sw=TAU*fract(t*(0.10+0.14*r1.y)+r3*5.0);
vec2 c=0.5+(r1-0.5)*0.60+vec2(0.10*sin(sw),0.03*cos(2.0*sw+1.0));
vec2 dl=(vec2(gx-cxi,gy-cyi)-c)*S;
dl.x-=slant*dl.y;
float par=dot(dl,vd);
float d=length(vec2(dl.x*vd.y-dl.y*vd.x,par/(1.0+(0.5+1.2*uWind)*(1.0-z))));
float rr=rad*(0.60+0.75*r1.x);
float al=smoothstep(rr+soft,rr-soft,d)*(0.72+0.28*smoothstep(rr*0.35,rr,d))*ex;
acc=acc+al*(1.0-acc);
}
}
return acc;
}
float hillY(float x,vec4 k,vec2 ph){
return k.x+k.y*sin(x*k.z+ph.x)+k.w*sin(x*k.z*2.3+ph.y);
}
float hillD(float x,vec4 k,vec2 ph){
return k.y*k.z*cos(x*k.z+ph.x)+k.w*k.z*2.3*cos(x*k.z*2.3+ph.y);
}
vec3 pine(vec2 q,float seed,float soft,float nz,float sw){
vec2 r=h22(vec2(seed,3.0));
vec2 r2=h22(vec2(seed,9.0));
float y=sat(q.y);
float x=q.x-((r.x-0.5)*0.30+sw)*y*y;
float sd=smoothstep(-0.03,0.03,x);
float ax=abs(x)*(1.0+(sd-0.5)*(r.y-0.5)*0.7);
float n=5.0+6.0*r2.x;
float env=(0.21+0.12*r2.y)*pow(1.0-y,0.75+0.5*r.y);
float v=((y+0.30*y*y)/1.30+(0.45+0.35*r2.y)*ax)*n+sd*(0.25+0.5*r.x)+(nz-0.5)*0.45;
float ft=fract(v);
float rt=h21(vec2(floor(v)+step(0.0,x)*17.0,seed));
float lobe=smoothstep(0.0,0.14,ft)*pow(1.0-ft,1.3);
float wt=max(env*(0.42+0.72*lobe)*(0.76+0.40*rt),0.012*(1.0-y));
float a=smoothstep(soft,-soft,ax-wt+(nz-0.5)*0.05)*step(0.0,q.y)*step(q.y,1.0);
float snow=smoothstep(0.02,0.20+soft*n,ft)*(1.0-smoothstep(0.34,0.95,ft));
return vec3(a,snow,sat(ax/max(wt,1e-3)));
}
vec4 pineRow(vec2 p,float x,vec4 hk,vec2 hp,float cell,float hMin,float hMax,float sink,float seed,float soft,float nz,
float dens,vec3 gap,vec3 cD,vec3 cL,vec3 cS,float lgt,float sw){
float i0=floor(x/cell);
vec4 acc=vec4(0.0);
for(int k=0;k<5;k++){
float id=i0+float(k)-2.0;
float iw=mod(id,89.0)+seed*7.0;
vec2 hr=h22(vec2(iw,seed+5.0));
float hs=fract(hr.x*13.7+hr.y*5.3);
float tx0=(id+0.05+0.90*hr.x)*cell;
float H=mix(hMin,hMax,hr.y*hr.y*(0.55+0.45*hr.y));
vec2 q=vec2(x-tx0,hillY(tx0,hk,hp)+sink-p.y)/H;
float clear=abs(mod(tx0-gap.x+gap.z*0.5,gap.z)-gap.z*0.5)-gap.y;
float grove=mix(0.06,0.78,smoothstep(0.38,0.62,vnp(vec2(tx0*dens+seed*3.0,seed))));
if(hs>grove&&clear>0.0&&q.y>0.0&&q.y<1.0&&abs(q.x)<0.50){
vec3 tr=pine(q,iw,soft/H,nz,sw);
float snowy=tr.y*(0.30+0.70*fract(hs*7.3));
float lit=sat(0.55+0.45*tr.z*(q.x<0.0?-lgt:lgt));
vec3 c=mix(mix(cD,cS,0.30),mix(cS,cL,lit),snowy);
c=mix(c,cD,0.28*(1.0-tr.z)*(1.0-snowy));
c*=0.92+0.16*nz;
acc=vec4(acc.rgb*(1.0-tr.x)+c*tr.x,acc.a*(1.0-tr.x)+tr.x);
}
}
return acc;
}
float treeline(float g,float seed){
float i0=floor(g);
float h=0.0;
for(int k=0;k<3;k++){
float id=i0+float(k)-1.0;
vec2 r=h22(vec2(mod(id,113.0),seed));
h=max(h,(0.35+0.65*r.y)*pow(sat(1.0-abs(g-id-0.5-(r.x-0.5)*0.9)/(0.40+0.30*r.x)),1.25));
}
return h;
}
vec4 sceneMain(vec2 xy){
vec2 uv=xy/uSize;
float asp=uSize.x/uSize.y;
vec2 p=vec2(uv.x*asp,uv.y);
float px=1.0/uSize.y;
float t=uTime;
float sv=mod(uSeed,61.0);
float calm=1.0-0.40*uSoft;
float lampOn=day4f(0.45,0.0,0.85,1.0);
float nightK=day4f(0.35,0.0,0.45,1.0);
float lgt=day4f(-1.0,-0.6,1.0,-0.3);
vec3 top=day4(hx(30.0,50.0,104.0),hx(74.0,98.0,144.0),hx(72.0,66.0,126.0),hx(7.0,11.0,32.0));
vec3 mid=day4(hx(70.0,98.0,154.0),hx(108.0,132.0,170.0),hx(142.0,102.0,156.0),hx(14.0,22.0,54.0));
vec3 low=day4(hx(150.0,172.0,214.0),hx(190.0,202.0,222.0),hx(238.0,170.0,182.0),hx(34.0,46.0,90.0));
float y=sat(p.y/0.70);
vec3 col=mix(top,mid,smoothstep(0.0,0.62,y));
col=mix(col,low,smoothstep(0.62,1.0,y));
vec2 cp=vec2(p.x*0.70+t*0.008+uFlow*0.010+sv,p.y*2.4);
float cl=fbp(cp+vec2(0.0,fbp3(cp*0.6+7.0)*0.8));
col*=0.88+0.24*cl+0.015*uLevel;
vec3 glowC=day4(hx(226.0,190.0,214.0),hx(255.0,238.0,226.0),hx(255.0,196.0,170.0),hx(96.0,116.0,176.0));
float gd=length((p-vec2(day4f(0.28,0.36,0.72,0.56)*asp,0.68))*vec2(0.42,1.0));
col+=glowC*exp(-gd*6.0)*day4f(0.26,0.20,0.34,0.20)*(1.0+0.3*uLevel);
vec3 snLit=day4(hx(160.0,188.0,232.0),hx(238.0,243.0,250.0),hx(255.0,216.0,226.0),hx(80.0,100.0,154.0));
vec3 snSh=day4(hx(70.0,100.0,164.0),hx(150.0,174.0,214.0),hx(148.0,124.0,188.0),hx(22.0,32.0,72.0));
vec3 firC=day4(hx(24.0,42.0,80.0),hx(54.0,74.0,96.0),hx(62.0,50.0,94.0),hx(7.0,12.0,30.0));
vec3 hazeC=day4(hx(156.0,178.0,220.0),hx(200.0,210.0,228.0),hx(240.0,184.0,198.0),hx(46.0,62.0,112.0));
vec3 lampC=hx(255.0,204.0,128.0);
float fogA=fbp3(vec2(p.x*1.3+t*0.012+uFlow*0.08+sv,p.y*7.0));
float fogB=fbp3(vec2(p.x*2.0-t*0.009+uFlow*0.16+5.0,p.y*9.0+3.0));
float gw=(gust(p.x+uFlow*0.2,t)-0.30)*(0.25+uWind)*0.035;
float mx=p.x+uFlow*0.03;
float mtn=0.625-0.085*fbp3(vec2(mx*1.1+5.0+sv,1.0))-0.015*fbp3(vec2(mx*5.0,3.0));
col=mix(col,mix(mix(snSh,snLit,0.5),low,0.76),smoothstep(-0.006,0.006,p.y-mtn)*0.85);
float f1x=p.x+uFlow*0.06;
float w1=0.676-0.022*vnp(vec2(f1x*2.0,4.0))-0.046*treeline(f1x/0.019,3.0)*(0.25+0.75*vnp(vec2(f1x*9.0,2.0)));
col=mix(col,mix(firC,hazeC,0.84),smoothstep(-0.007,0.007,p.y-w1)*0.90);
col=mix(col,hazeC,0.45*smoothstep(0.30,0.75,fogA)*smoothstep(0.60,0.69,p.y));
vec4 hk0=vec4(0.722,0.008,3.1,0.004);
vec2 hp0=vec2(3.0+sv,5.0);
float x0=p.x+uFlow*0.09;
float nz0=fbp3(vec2(x0*30.0,p.y*34.0));
vec4 r0=pineRow(p,x0,hk0,hp0,0.021,0.040,0.090,0.0,0.0,px*2.2,nz0,4.0,vec3(0.0,-1.0,1.0),firC,snLit,snSh,lgt,gw);
col=col*(1.0-r0.a)+mix(r0.rgb,hazeC*r0.a,0.70);
col=mix(col,mix(firC,hazeC,0.74),smoothstep(-0.004,0.004,p.y-hillY(x0,hk0,hp0)+0.012)*0.9);
vec4 hk1=vec4(0.748,0.016,2.3,0.008);
vec2 hp1=vec2(1.0+sv,2.0);
float x1=p.x+uFlow*0.12;
float nz1=fbp3(vec2(x1*22.0,p.y*26.0));
vec4 r1=pineRow(p,x1,hk1,hp1,0.046,0.050,0.130,0.004,1.0,px*1.6,nz1,2.6,vec3(0.0,-1.0,1.0),firC,snLit,snSh,lgt,gw);
col=col*(1.0-r1.a)+mix(r1.rgb,hazeC*r1.a,0.50+0.25*smoothstep(0.10,0.0,hillY(x1,hk1,hp1)-p.y));
float h1=hillY(x1,hk1,hp1);
vec3 c1=mix(snLit,snSh,sat(0.36-4.0*hillD(x1,hk1,hp1)*lgt+0.6*sat((p.y-h1)/0.07)+(nz1-0.5)*0.15));
col=mix(col,mix(c1,hazeC,0.48),smoothstep(-0.0035,0.0035,p.y-h1));
col=mix(col,hazeC,(0.18+0.44*smoothstep(0.30,0.80,fogB))*smoothstep(0.68,0.77,p.y)*smoothstep(0.88,0.80,p.y));
vec4 hk2=vec4(0.836,0.026,1.5,0.012);
vec2 hp2=vec2(4.0+sv*1.7,1.0);
float x2=p.x+uFlow*0.21;
float lper=1.6*asp;
float lx0=0.43*asp;
float lxx=mod(x2-lx0+lper*0.5,lper)-lper*0.5;
float lbase=hillY(x2-lxx,hk2,hp2)+0.010;
vec2 lv=vec2(lxx,p.y-(lbase-LAMP_H));
float ld=length(lv);
float cone=smoothstep(0.85,0.20,abs(lv.x)/max(lv.y,1e-3))*step(0.0,lv.y)*smoothstep(LAMP_H*1.3,LAMP_H*0.5,lv.y);
float lampK=(0.9*cone+exp(-ld*ld/0.0020))*lampOn;
float nz2=fbp3(vec2(x2*18.0,p.y*22.0));
float wl=exp(-ld*ld/0.020)*lampOn*0.8;
vec4 r2=pineRow(p,x2,hk2,hp2,0.070,0.100,0.290,0.006,2.0,px*1.3,nz2,1.7,vec3(lx0,0.070,lper),firC,mix(snLit,lampC,wl),mix(snSh,lampC*0.55,wl*0.6),lgt,gw);
col=col*(1.0-r2.a)+mix(r2.rgb,hazeC*r2.a,0.14+0.34*smoothstep(0.12,0.0,hillY(x2,hk2,hp2)-p.y));
float h2=hillY(x2,hk2,hp2);
vec3 c2=mix(snLit,snSh,sat(0.28-4.0*hillD(x2,hk2,hp2)*lgt+0.62*sat((p.y-h2)/0.10)+(fbp3(vec2(x2*2.2,p.y*34.0))-0.5)*0.40));
c2+=snLit*0.08*exp(-max(p.y-h2,0.0)/0.005);
float pool=exp(-sq(lxx/0.105)-sq((p.y-lbase-0.003)/0.013))*lampOn;
c2=mix(c2,mix(lampC,vec3(1.0),0.3*pool),sat(pool)*(0.35+0.65*nightK));
col=mix(col,mix(c2,hazeC,0.16),smoothstep(-0.0022,0.0022,p.y-h2));
vec3 lampBody=mix(firC,hazeC,day4f(0.40,0.50,0.38,0.15));
float pole=smoothstep(px*0.8,-px*0.4,abs(lv.x)-0.0010-0.0012*smoothstep(LAMP_H-0.012,LAMP_H,lv.y))*step(0.008,lv.y)*step(lv.y,LAMP_H+0.004);
col=mix(col,lampBody,pole*0.9);
float glass=smoothstep(px,-px,max(abs(lv.x)-0.0036-0.22*(0.008-lv.y),abs(lv.y)-0.008));
float roof=smoothstep(px,-px,max(abs(lv.x)-0.0080-1.0*(lv.y+0.012),abs(lv.y+0.0100)-0.0025));
float capSnow=smoothstep(px,-px,length(vec2(lv.x/0.0070,(lv.y+0.0140)/0.0032))-1.0)*step(lv.y,-0.0120);
col=mix(col,mix(mix(lampBody,hazeC,0.3),hx(255.0,240.0,204.0),lampOn),glass);
col=mix(col,lampBody,roof*0.95);
col=mix(col,mix(snSh,snLit,0.7),capSnow);
col=mix(col,hazeC,0.30*smoothstep(0.35,0.80,fogA)*smoothstep(0.76,0.84,p.y)*smoothstep(0.93,0.86,p.y));
vec4 hk3=vec4(0.922,0.022,1.9,0.010);
vec2 hp3=vec2(2.0+sv*2.3,0.0);
float x3=p.x+uFlow*0.34;
float nz3=fbp3(vec2(x3*9.0,p.y*12.0));
float rip3=fbp3(vec2(x3*2.6,p.y*30.0));
vec4 r3=pineRow(p,x3,hk3,hp3,0.150,0.200,0.370,0.010,3.0,px*2.6,nz3,0.9,vec3(lx0+0.02,0.150,lper*0.5),firC,snLit,snSh,lgt,gw);
col=col*(1.0-r3.a)+mix(r3.rgb,hazeC*r3.a,0.06+0.26*smoothstep(0.14,0.0,hillY(x3,hk3,hp3)-p.y))*(1.0+0.25*lampK);
float h3=hillY(x3,hk3,hp3);
vec3 c3=mix(snLit,snSh,sat(0.24-3.5*hillD(x3,hk3,hp3)*lgt+0.55*sat((p.y-h3)/0.09)+(rip3-0.5)*0.45));
c3+=snLit*0.08*exp(-max(p.y-h3,0.0)/0.007);
col=mix(col,c3,smoothstep(-0.004,0.004,p.y-h3));
float nper=0.84*asp;
float x4=p.x+uFlow*0.52;
float ng=(x4-0.08*asp)/nper+0.5;
float ni=floor(ng);
float nz4=fbp3(vec2(x4*5.0,p.y*7.0));
for(int k=0;k<2;k++){
float fk=float(k);
float iw=mod(ni,53.0)*3.0+fk+sv;
vec2 hr=h22(vec2(iw,11.0));
float H=(0.44+0.08*hr.x)*(1.0-0.40*fk);
float cxn=fk*(0.11+0.06*hr.y)*(hr.x<0.5?1.0:-1.0);
vec2 q=vec2((fract(ng)-0.5)*nper-cxn,1.06-0.03*fk-p.y)/H;
if(q.y>0.0&&q.y<1.0&&abs(q.x)<0.50){
vec3 tr=pine(q,iw,0.036,nz4,gw*1.3);
float snowy=tr.y*(0.45+0.4*hr.y);
vec3 c=mix(mix(firC,snSh,0.35),mix(snSh,snLit,0.6),snowy);
col=mix(col,c*(1.0+0.3*lampK),tr.x*0.96);
}
}
float h4=0.975+0.014*sin(x4*2.6+2.0+sv)+0.007*sin(x4*5.9);
vec3 c4=mix(snLit,snSh,sat(0.30+0.5*sat((p.y-h4)/0.06)+(nz4-0.5)*0.3));
col=mix(col,c4,smoothstep(-0.006,0.006,p.y-h4));
vec2 sg=(p+vec2(uFlow*0.25,0.0))/0.011;
vec2 si=floor(sg);
vec2 sh2=h22(vec2(mod(si.x,127.0),si.y)+3.0);
float spark=glow(length(sg-si-0.5-(sh2-0.5)*0.7)*0.011,px*1.1)*pow(0.5+0.5*sin(t*(0.4+1.1*sh2.x)+sh2.y*40.0),6.0);
col+=(snLit*0.5+lampC*pool*1.2)*spark*step(0.72,sh2.y)*smoothstep(-0.002,0.004,p.y-h2)*calm*0.8;
float dr=fbp3(vec2(p.x*2.4+t*0.10+uFlow*0.5,p.y*14.0-t*0.02));
float drift=smoothstep(0.50,0.85,dr)*smoothstep(0.66,0.84,p.y)*(0.10+0.30*uWind);
col=mix(col,snLit,drift*calm);
col+=lampC*((0.20*cone+0.30*exp(-ld*ld/0.0020)+0.10*exp(-ld*ld/0.020))*nightK+0.60*exp(-ld*ld/0.00010))*lampOn*(1.0+0.14*uLevel);
vec3 flake=day4(hx(176.0,200.0,240.0),hx(250.0,252.0,255.0),hx(255.0,230.0,238.0),hx(104.0,124.0,176.0));
float topFade=mix(0.62,1.0,smoothstep(0.30,0.62,p.y));
float dimN=day4f(0.0,0.0,0.2,0.9);
for(int i=0;i<7;i++){
float fi=float(i);
float a=snowLayer(p,fi,t,px);
float op=mix(0.80,0.34,smoothstep(2.0,6.0,fi))*(i<2?1.0-0.5*uSoft:1.0);
float lw=sat(lampK*2.2*exp(-sq(fi-1.2)/3.0))*day4f(0.5,0.0,0.5,1.0);
float k=a*op*calm*topFade*mix(1.0,0.50+1.6*lw,dimN);
col=mix(col,mix(flake,hx(255.0,242.0,210.0),lw)*(1.0+0.6*lw),sat(k));
}
if(lampK>0.02){
float ex1=snowLayer(p+vec2(3.7,1.3),1.0,t,px)+snowLayer(p+vec2(7.1,4.9),2.0,t,px);
col+=hx(255.0,236.0,196.0)*sat(ex1)*sat(lampK*1.6)*day4f(0.45,0.0,0.45,1.0)*calm;
}
return finish(col,xy);
}
uniform float uDim;
uniform vec4 uVeil;
uniform float uFade;
void main() {
    vec4 c = sceneMain(vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y));
    gl_FragColor = vec4(mix(c.rgb, uVeil.rgb, uVeil.a) * (1.0 - uDim), uFade);
}
