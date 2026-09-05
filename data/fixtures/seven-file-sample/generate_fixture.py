#!/usr/bin/env python3
"""Deterministic seven-file fixture in the official CSV formats (offline generation, D-028)."""
import csv, random, datetime as dt, os, sys, uuid
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
rnd = random.Random(20260905)
def fmt_date(d): return d.strftime("%B %-d, %Y")
def fmt_ts(t): return t.strftime("%B %-d, %Y, %-I:%M %p")
def comma(n): return f"{n:,}"
BASE_START, BASE_END = dt.date(2026,5,4), dt.date(2026,5,31)
CUR_START, CUR_END = dt.date(2026,6,1), dt.date(2026,6,7)
days = [BASE_START + dt.timedelta(d) for d in range((CUR_END-BASE_START).days+1)]
trips=[]; legs=[]; bills=[]; fb=[]; alerts=[]
tid = 3000000; rider = 100000
PIN_VENDORS=["Rohan Mikhailov Travel","Pooja Mikhailov Travel"]
PIN_SITES=["Clearwater Campus","Oakmont Office"]
SHIFTS=["09:00","15:30"]
colliding_ids=[]
for d in days:
    current = d >= CUR_START
    month = "may" if d.month==5 else "June"
    for i in range(20):  # pinnacle-Slc: 20 trips/day
        tid += 1
        site = PIN_SITES[i % 2]; shift = SHIFTS[(i//2) % 2]; direction = "LOGIN" if shift=="09:00" else "LOGOUT"
        vendor = PIN_VENDORS[i % 2]
        # delay design: baseline 10% (2 of 20) ; current 25% (5 of 20), concentrated in Clearwater LOGIN
        if current: delayed = i in (0,2,4,6,8)   # all Clearwater (even i), LOGIN shifts i//2 even -> i in 0,1,4,5,8,9.. mixed; keep simple
        else: delayed = i in (0,11)
        reason = "NODELAY"
        delay_min = 0
        if delayed:
            reason = ["DRIVER","EMPLOYEE","TRAFFIC"][(i+d.day)%3]
            delay_min = 15 + (i*7 % 40)
            if current and i==8: delay_min = 700    # capped >600
            if d == dt.date(2026,5,10) and i==0: delay_min = 2000  # quarantined > 1440
        start = dt.datetime.combine(d, dt.time(9,0)) if direction=="LOGIN" else dt.datetime.combine(d, dt.time(15,30))
        pse = int(start.timestamp()); pee = pse + 3600
        cap = 4; actual_cnt = 2 if i != 3 else 5   # i==3 exceeds capacity
        fuel = "Electric" if i % 5 == 0 else "Diesel"
        trips.append({"business_unit":"pinnacle-Slc","office":site,"product_type":"CAB","trip_date":fmt_date(d),"shift_type":shift,
            "trip_id":comma(tid),"trip_direction":direction,"actual_escort":"false","vendor_id":vendor,
            "planned_cab_registration":f"KA {i:02d} AB","actual_cab_registration":f"KA {i:02d} AB","actual_cab_capacity":cap,
            "planned_km": "12.5" if not (month=="June" and i==1) else '"12,5"'.strip('"').replace(",","."),
            "traveled_km":"13.1","planned_start_epoch":comma(pse),"planned_end_epoch":comma(pee),
            "actual_start_epoch":comma(pse+delay_min*60),"actual_end_epoch":comma(pee+delay_min*60),"delay_reason":reason,
            "delay_minutes":comma(delay_min),"route_source":"AUTO","actual_cab_fuel_type":fuel,
            "is_driver_nc":"false" if month=="June" else "False","is_cab_nc":"false" if month=="June" else "False",
            "trip_nodal":"NA","plannedemployee_cnt":2,"actualemployee_cnt":actual_cnt,"noshow_cnt":0, "_month":month})
        # legs: 2 riders
        for r in range(2):
            rider += 1
            pp = pse + 300*r; pd_ = pee
            # on-time design: baseline 90% within 10 min; current 70%
            late = (rnd.random() < 0.30) if current else (rnd.random() < 0.10)
            dev = 900 if late else 120
            noshow = (r==1 and i==7)  # one no-show per day
            legs.append({"business_unit":"pinnacle-Slc","office":site,"product_type":"CAB","trip_date":d.isoformat(),"shift_type":shift,
                "trip_id":tid,"planned_pickup_epoch":f"{pp}.0","planned_drop_epoch":f"{pd_}.0",
                "actual_pickup_epoch":"" if noshow else f"{pp+dev}.0","actual_drop_epoch":"" if noshow else f"{pd_+dev}.0",
                "planned_km":"6.2","traveled_km":"-1.5" if (i==9 and r==0 and d==CUR_START) else "6.4","stwid":rider,
                "signintype":"" if noshow else "Planned","gender":"FEMALE" if r else "MALE","emp_role":"employee",
                "boarding_status":"Not Boarded" if noshow else "Boarded","not_boarding_reason":"NO_SHOW" if noshow else "",
                "is_no_show":"True" if noshow else "False"})
            fb.append({"business_unit":"pinnacle-Slc","trip_id":comma(tid),"trip_type":direction,"trip_date":fmt_ts(start),
                "stwid":comma(rider),"route_rating":5,"driver_rating":(2 if (i==5 and r==0) else 5),"cab_rating":5,"safety_rating":4,
                "marshal_rating":0,"creation_time":fmt_ts(start+dt.timedelta(hours=2))})
        if i==0 and d==CUR_START:  # duplicate leg row (exact same key)
            legs.append(dict(legs[-2]))
        bills.append({"business_unit":"pinnacle-Slc","office":site,"vendor":vendor,
            "cycle_start":fmt_ts(dt.datetime(d.year,d.month,1)),"cycle_end":fmt_ts(dt.datetime(d.year,d.month,31 if d.month==5 else 30)),
            "trip_id":str(tid),"contract":"4S-DSL","slab_name":"Medium","total_trip_km":"12.5","trip_cost":comma(1000 + 20*(i%5))})
        if current and i==0: bills.append(dict(bills[-1]))   # exact duplicate line
    # orbit-Slc: 5 trips/day, three ids collide with pinnacle ids on 2026-05-04
    for j in range(5):
        oid = (3000001 + j) if (d==BASE_START and j<3) else 1200000 + len(trips)
        if d==BASE_START and j<3: colliding_ids.append(oid)
        start = dt.datetime.combine(d, dt.time(10,0)); pse=int(start.timestamp())
        trips.append({"business_unit":"orbit-Slc","office":"Eastgate Office","product_type":"BUS","trip_date":fmt_date(d),"shift_type":"10:00",
            "trip_id":comma(oid),"trip_direction":"LOGIN","actual_escort":"true","vendor_id":"Rohan Mikhailov Travel",
            "planned_cab_registration":"OR 01 ZZ","actual_cab_registration":"OR 01 ZZ","actual_cab_capacity":12,"planned_km":"20.0","traveled_km":"21.0",
            "planned_start_epoch":comma(pse),"planned_end_epoch":comma(pse+3600),"actual_start_epoch":comma(pse),"actual_end_epoch":comma(pse+3600),
            "delay_reason":"NODELAY","delay_minutes":"0","route_source":"SHUTTLE_SERVICE","actual_cab_fuel_type":"Electric","is_driver_nc":"false","is_cab_nc":"false",
            "trip_nodal":"SHUTTLE","plannedemployee_cnt":8,"actualemployee_cnt":8,"noshow_cnt":0,"_month":month})
        bills.append({"business_unit":"orbit-Slc","office":"Eastgate Office","vendor":"Rohan Mikhailov Travel",
            "cycle_start":fmt_ts(dt.datetime(d.year,d.month,1)),"cycle_end":fmt_ts(dt.datetime(d.year,d.month,15)),
            "trip_id":str(oid),"contract":"BUS-12","slab_name":"","total_trip_km":"0","trip_cost":comma(1170)})
# adjustments and null trip id lines
bills.append({"business_unit":"pinnacle-Slc","office":"Clearwater Campus","vendor":"Pooja Mikhailov Travel","cycle_start":fmt_ts(dt.datetime(2026,5,1)),
    "cycle_end":fmt_ts(dt.datetime(2026,5,31)),"trip_id":"3000005","contract":"4S-DSL","slab_name":"","total_trip_km":"0","trip_cost":"-3,480"})
bills.append({"business_unit":"pinnacle-Slc","office":"Clearwater Campus","vendor":"Pooja Mikhailov Travel","cycle_start":fmt_ts(dt.datetime(2026,5,1)),
    "cycle_end":fmt_ts(dt.datetime(2026,5,31)),"trip_id":"","contract":"4S-DSL","slab_name":"","total_trip_km":"5","trip_cost":"900"})
# feedback for orbit: only 2 rows (low coverage)
for k in range(2):
    fb.append({"business_unit":"orbit-Slc","trip_id":comma(1200000+20+k),"trip_type":"LOGIN","trip_date":fmt_ts(dt.datetime(2026,5,5,10)),
        "stwid":comma(500000+k),"route_rating":4,"driver_rating":4,"cab_rating":4,"safety_rating":4,"marshal_rating":3,"creation_time":fmt_ts(dt.datetime(2026,5,5,12))})
# alerts: pinnacle sign-off violations 200/week in weeks of May 4 and May 11, then zero; steady other alerts
pin_ids = [t["trip_id"] for t in trips if t["business_unit"]=="pinnacle-Slc"]
def alert(bu, trip_id, etype, when, sev, ack_min, src="MOBILE", stw="0"):
    alerts.append({"business_unit":bu,"trip_id":trip_id,"stwid":stw,"event_id":str(uuid.UUID(int=rnd.getrandbits(128))),"event_type":etype,
        "start_time":fmt_ts(when),"acknowledge_time":fmt_ts(when+dt.timedelta(minutes=ack_min)) if ack_min is not None else "NA",
        "state_text":"CLOSED","severity":sev,"source":src})
for d in days:
    idx = days.index(d)
    day_trips = [t for t in trips if t["business_unit"]=="pinnacle-Slc" and t["trip_date"]==fmt_date(d)]
    when = dt.datetime.combine(d, dt.time(9,5))
    if dt.date(2026,5,4) <= d <= dt.date(2026,5,17):
        for k in range(28 if d.weekday()<6 else 32):  # ~200/week
            alert("pinnacle-Slc", day_trips[k%20]["trip_id"], "EMPLOYEE_SIGN_OFF_TIME_VIOLATION", when, "NA", None, "NA")
    alert("pinnacle-Slc", day_trips[0]["trip_id"], "DEVICE_NOT_REACHABLE", when, "Sev-3", 5)
    alert("pinnacle-Slc", day_trips[1]["trip_id"], "EMPLOYEE_GEOFENCE_VIOLATION", when, "False", 3)
    if idx % 7 == 0:
        alert("pinnacle-Slc", day_trips[2]["trip_id"], "PANIC_MOBILE", when, "Sev-1", 2, "MOBILE_APP")
        alert("pinnacle-Slc", day_trips[3]["trip_id"], "OVER_SPEEDING", when, "Sev-2", 8, "DEVICE")
    if d >= CUR_START:  # extra device unreachable in the current week (doubling)
        alert("pinnacle-Slc", day_trips[4]["trip_id"], "DEVICE_NOT_REACHABLE", when, "Sev-3", 6)
# orbit: WOMAN_TRAVELLING_ALONE
for t in [x for x in trips if x["business_unit"]=="orbit-Slc"][:25]:
    alert("orbit-Slc", t["trip_id"], "WOMAN_TRAVELLING_ALONE", dt.datetime(2026,5,6,10), "Sev-2", 1, "DEVICE", "500,001")
def write(name, rows, cols):
    with open(os.path.join(OUT,name),"w",newline="") as f:
        w=csv.DictWriter(f, fieldnames=cols, extrasaction="ignore", quoting=csv.QUOTE_MINIMAL); w.writeheader(); w.writerows(rows)
ride_cols=["business_unit","office","product_type","trip_date","shift_type","trip_id","trip_direction","actual_escort","vendor_id","planned_cab_registration","actual_cab_registration","actual_cab_capacity","planned_km","traveled_km","planned_start_epoch","planned_end_epoch","actual_start_epoch","actual_end_epoch","delay_reason","delay_minutes","route_source","actual_cab_fuel_type","is_driver_nc","is_cab_nc","trip_nodal","plannedemployee_cnt","actualemployee_cnt","noshow_cnt"]
write("Ride_data _trip-may_2026.csv", [t for t in trips if t["_month"]=="may"], ride_cols)
write("Ride_data _trip-June_2026.csv", [t for t in trips if t["_month"]=="June"], ride_cols)
write("emp_Data.csv", legs, ["business_unit","office","product_type","trip_date","shift_type","trip_id","planned_pickup_epoch","planned_drop_epoch","actual_pickup_epoch","actual_drop_epoch","planned_km","traveled_km","stwid","signintype","gender","emp_role","boarding_status","not_boarding_reason","is_no_show"])
write("bill_data.csv", bills, ["business_unit","office","vendor","cycle_start","cycle_end","trip_id","contract","slab_name","total_trip_km","trip_cost"])
write("trip_feedback.csv", fb, ["business_unit","trip_id","trip_type","trip_date","stwid","route_rating","driver_rating","cab_rating","safety_rating","marshal_rating","creation_time"])
write("alerts_data.csv", alerts, ["business_unit","trip_id","stwid","event_id","event_type","start_time","acknowledge_time","state_text","severity","source"])
print("trips",len(trips),"legs",len(legs),"bills",len(bills),"fb",len(fb),"alerts",len(alerts),"colliding",colliding_ids)
