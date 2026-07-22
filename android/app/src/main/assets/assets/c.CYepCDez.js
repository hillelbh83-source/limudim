import{j as t,m as o}from"./e.CTYcQwqj.js";import{B as e}from"./c.cXnkxpvy.js";import{M as r}from"./c.Clmg6Sgb.js";import"./c.CuDEm0KI.js";import"./c.BW-XyaGS.js";import"./c.ChxAWldX.js";import"./c.DI_5yP3v.js";import"./c.BhE4TZsF.js";import"./c.BRWYH1ou.js";import"./c.FZjG-SCy.js";import"./c.BzSG06Ep.js";import"./c.HTeLTF9G.js";import"./c.D2ozk-_G.js";import"./c.B8uU1cMT.js";import"./c.p018AHG0.js";const n=({title:i,code:s})=>t.jsxs("div",{className:"ds-code-block p-5 rounded-lg text-sm",dir:"ltr",children:[t.jsx("div",{className:"text-emerald-700 dark:text-emerald-300 font-semibold",children:i}),t.jsx("pre",{className:"mt-3 whitespace-pre-wrap leading-6",children:s})]}),d=()=>t.jsxs("div",{className:"rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 p-5 shadow-sm",children:[t.jsx("h4",{className:"font-bold text-center text-slate-800 dark:text-slate-100 mb-4",children:"Bottom-up: מחשבים לפי אורך עולה"}),t.jsx("div",{className:"grid grid-cols-6 gap-2",dir:"ltr",children:[0,1,2,3,4,5].map(i=>t.jsxs("div",{className:"rounded-lg border border-indigo-200 dark:border-indigo-800 overflow-hidden",children:[t.jsxs("div",{className:"bg-indigo-600 text-white text-center text-xs py-1",children:["j=",i]}),t.jsxs("div",{className:"h-14 flex items-center justify-center font-bold text-indigo-800 dark:text-indigo-100 bg-indigo-50 dark:bg-indigo-900/30",children:["r[",i,"]"]})]},i))}),t.jsxs("p",{className:"text-sm text-slate-600 dark:text-slate-300 text-center mt-4",children:["כשמחשבים את ",t.jsx("span",{dir:"ltr",children:"r[j]"}),", כל הערכים ",t.jsx("span",{dir:"ltr",children:"r[0..j-1]"})," כבר מוכנים."]})]}),O=()=>t.jsxs(o.div,{initial:{opacity:0,y:12},animate:{opacity:1,y:0},transition:{duration:.35},className:"space-y-6",children:[t.jsx("h1",{className:"text-3xl font-bold text-slate-900 dark:text-white",children:"Memoization ו-Bottom-up ב-Rod Cutting"}),t.jsxs(e,{type:"concept",title:"Top-down עם זיכרון",children:[t.jsxs("p",{children:["הגישה הראשונה לתכנות דינמי שומרת את המבנה הרקורסיבי, אבל מוסיפה מערך זיכרון",t.jsx(r,{children:String.raw`r[0..n]`}),". אם כבר חישבנו את התשובה עבור אורך מסוים, מחזירים אותה מיד."]}),t.jsx(r,{block:!0,children:String.raw`r[i]=\text{maximum revenue for a rod of length }i`})]}),t.jsx(e,{type:"algorithm",title:"MEMOIZED-CUT-ROD",children:t.jsx(n,{title:"Top-down dynamic programming",code:`MEMOIZED-CUT-ROD(p, n)
    let r[0..n] be a new array
    for i = 0 to n
        r[i] = -∞
    return MEMOIZED-CUT-ROD-AUX(p, n, r)

MEMOIZED-CUT-ROD-AUX(p, n, r)
    if r[n] >= 0
        return r[n]

    if n == 0
        q = 0
    else
        q = -∞
        for i = 1 to n
            q = max(q, p[i] + MEMOIZED-CUT-ROD-AUX(p, n - i, r))

    r[n] = q
    return q`})}),t.jsxs(e,{type:"explanation",title:"למה זה הופך לריבועי?",children:[t.jsxs("p",{children:["יש רק ",t.jsx(r,{children:"n+1"})," תתי-בעיות שונות: אורכי מוט",t.jsx(r,{children:String.raw`0,1,\dots,n`}),". עבור תת-בעיה באורך ",t.jsx(r,{children:"j"})," בודקים",t.jsx(r,{children:"j"})," חיתוכים אפשריים."]}),t.jsx(r,{block:!0,children:String.raw`\sum_{j=1}^{n} j=\Theta(n^2)`}),t.jsx("p",{children:"לכן כל תת-בעיה נפתרת פעם אחת, ושאר הקריאות אליה חוזרות מיד מהמערך."})]}),t.jsx(e,{type:"concept",title:"Bottom-up: בלי רקורסיה",children:t.jsxs("p",{children:["בגישה מלמטה למעלה מתחילים מ-",t.jsx(r,{children:String.raw`r[0]=0`}),", ואז ממלאים את",t.jsx(r,{children:String.raw`r[1],r[2],\dots,r[n]`})," לפי סדר. כך כל ערך שצריך כבר נמצא בטבלה."]})}),t.jsx(d,{}),t.jsxs(e,{type:"algorithm",title:"BOTTOM-UP-CUT-ROD",children:[t.jsx(n,{title:"Bottom-up dynamic programming",code:`BOTTOM-UP-CUT-ROD(p, n)
    let r[0..n] be a new array
    r[0] = 0
    for j = 1 to n
        q = -∞
        for i = 1 to j
            q = max(q, p[i] + r[j - i])
        r[j] = q
    return r[n]`}),t.jsxs("p",{className:"mt-4",children:["זמן הריצה נשאר ",t.jsx(r,{children:String.raw`\Theta(n^2)`}),", אבל אין מחסנית רקורסיה והסדר של החישוב ברור מאוד."]})]}),t.jsx(e,{type:"tip",title:"איך לבחור בין top-down ל-bottom-up?",children:t.jsx("p",{children:"אם קל לכתוב את הרקורסיה ורק רוצים למנוע חישוב חוזר, Memoization טבעי מאוד. אם ברור מראש מה סדר התלות בין התתי-בעיות, Bottom-up בדרך כלל נקי וחסכוני יותר."})})]});export{O as default};
