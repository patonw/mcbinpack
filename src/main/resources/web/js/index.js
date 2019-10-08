const { h, Component, createRef, render } = preact;
const { range, interval, defer, fromEvent, Subject, merge } = rxjs;
const { map, filter, catchError, of, switchMap, tap, share, pluck, withLatestFrom, retry } = rxjs.operators;
const { webSocket } = rxjs.webSocket;
const { ajax } = rxjs.ajax;

const html = htm.bind(h);

const spec = localStorage.getItem("spec") || `{
    "solver": "mcts",
    "width": 200,
    "height": 300,
    "items": []
}`
const vlOpt = {
    actions: false
};

const  vlSpec = {
   $schema: 'https://vega.github.io/schema/vega-lite/v4.json',
   data: {name: 'table'},
   width: 400,

   autosize: {
    type: "fit",
    contains: "padding",
    resize: true
   },
   mark: {
    type: 'line',
    point: true
   },
   encoding: {
     x: {field: 'time', type: 'quantitative', scale: {zero: true}},
     y: {field: 'score', type: 'quantitative', scale: {zero: false}},
     tooltip: {field: 'step', type: 'quantitative'}
   }
 };

class History extends Component {
    constructor(props) {
        super(props);
        this.ref = createRef();
    }

    shouldComponentUpdate() {
        return false;
    }

    componentDidMount() {
        const { source } = this.props;

        vegaEmbed(this.ref.current, vlSpec, vlOpt).then(chart => {
            console.log("Started vega chart ", chart);
            source.subscribe(evt => {
                if (evt.type == "reset") {
                    chart.view.remove('table', () => true).run();
                }
                else if (evt.step === 0) {
                    const changeset = vega.changeset()
                        .remove(() => true)
                        .insert(evt);
                    chart.view.change('table', changeset).run();
                }
                else
                    chart.view.insert('table', evt).run();
            });
        });

    }

    render() {
        return html`
            <div>
                <div ref=${this.ref}></div>
            </div>
        `
    }
}

class Visualizer extends Component {
    constructor(props) {
        super(props);
        this.ref = createRef();
    }

    shouldComponentUpdate() {
        return false;
    }

    drawSvg(data) {
        const { width, height, splits, items, rejects } = data;

        const box = d3.select(this.ref.current)
            .selectAll("rect.box")
            .data([{width, height}]);
         box.enter()
            .append("rect")
            .classed("box", true)
            .merge(box)
            .attr("x", 0)
            .attr("y", 0)
            .attr("width", width)
            .attr("height", height)
            .attr("style", "fill:white;stroke:black;stroke-width:2")
        box.exit().remove();

        const u = d3.select(this.ref.current)
            .selectAll("rect.item")
            .data(items)
        u.enter()
            .append("rect")
            .classed("item", true)
            .merge(u)
            .attr("style", "fill:gray;stroke:black;stroke-width:1")
            .attr("x", a => a.start.x)
            .attr("y", a => a.start.y)
            .attr("width", a => a.width)
            .attr("height", a => a.height)
        u.exit().remove()

        const r = d3.select(this.ref.current)
            .selectAll("rect.reject")
            .data(rejects)
        r.enter()
            .append("rect")
            .classed("reject", true)
            .merge(r)
            .attr("style", "fill:red;stroke:black;stroke-width:1")
            .attr("x", a => a.start.x)
            .attr("y", a => a.start.y)
            .attr("width", a => a.width)
            .attr("height", a => a.height)
        r.exit().remove()

        const z = d3.select(this.ref.current)
            .selectAll("line.splits")
            .data(splits)
        z.enter()
            .append("line")
            .classed("splits", true)
            .merge(z)
            .attr("x1", a => a.start.x)
            .attr("y1", a => a.start.y)
            .attr("x2", a => a.end.x)
            .attr("y2", a => a.end.y)
            .attr("style", "stroke:blue;stroke-width:1")
        z.exit().remove()
    }

    componentWillReceiveProps(nextProps) {
        const { data } = nextProps
        this.drawSvg(data)
    }

    render() {
        const {width, height} = this.props;
        return html`
            <svg ref=${this.ref} width=${width} height=${height}>
            </svg>
        `
    }
}

class SolutionBox extends Component {
    constructor(props) {
        super(props)
        this.state = {
            splits: [],
            items: [],
            rejects: [],
            width: 0,
            height: 0,
            score: 0
        }
    }

    componentDidMount() {
        const { source } = this.props;
        this.subscription = source.subscribe(data => this.setState({...this.state, ...data}));
    }

    componentWillUnmount() {
        if (this.subscription) {
            console.log("Unsubscribing!");
            this.subscription.unsubscribe();
            this.subscription = null;
        }
    }

    render() {
        const { width, height, source } = this.props
        const { splits, score } = this.state

        return html`
            <div>
                <${Visualizer} width=${width} height=${height} data=${this.state} />
                <div>Score: ${score}</div>
            </div>
        `
    }
}

class ProgressBar extends Component {
    constructor(props) {
        super(props)
        this.state = {
            value: 100,
            max: 100,
            pct: 0,
        }
    }

    componentDidMount() {
        const { source } = this.props

        source.subscribe(evt => {
            const { value, max, pct } = evt;
            //console.log("New progress", evt)
            this.setState({
                ...this.state,
                value,
                max,
                pct
            })
        })
    }

    render() {
        const { value, max, pct } = this.state;
        if (value === max) {
            return null
        }

        return html`
            <progress class="progress" value="${value}" max="${max}">${pct}%</progress>
        `
    }
}

class App extends Component {
    constructor(props) {
        super(props);

        this.buttonRef = createRef();
        this.specRef = createRef();
        this.genRef = createRef();

        var url = new URL('/ws', window.location.href);
        url.protocol = url.protocol.replace('http', 'ws');

        this.sock = webSocket({
            url: url.href,
            serializer: (msg) => msg,
        })

        const sock$ = this.sock.pipe(
//            tap(evt => console.log("Socket event", evt)),
            share()
        )

        this.reset = new Subject();
        this.finish = new Subject();

        this.solution = sock$.pipe(
            filter(evt => evt["@type"] === "SampleResult"),
        )

        this.progress = sock$.pipe(
            filter(evt => evt["@type"] === "ProgressReport"),
        )

        this.history = merge(
            this.solution.pipe(
                withLatestFrom(this.progress),
                map(([sol, prog]) => ({
                    type: "score",
                    step: prog.value,
                    time: prog.time,
                    score: sol.score,
                })),
            ),
            this.reset.pipe(
                map(() => ({
                    type: "reset"
                }))
            ),
            this.progress.pipe(
                filter(prog => prog.pct === 100),
                withLatestFrom(this.solution),
                map(([prog, sol]) => ({
                    type: "score",
                    step: prog.value,
                    time: prog.time,
                    score: sol.score,
                }))
            )
        );

        this.state = {
            spec
        };
    }

    sampleRequest() {
        const { spec } = this.state;
        const body = spec
//        console.log("Sending", body);

        const req$ = ajax({
            url: "/sample",
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body,
        });
        return req$.pipe(
            catchError(err => {
                console.log('error: ', err);
                return of(err)
            })
        )
    }

    componentDidMount() {
        const that = this;
        const source$ = fromEvent(this.buttonRef.current, "click");
        // TODO just go back to onClick handler
        source$.subscribe(evt => {
            // Can't figure out how to break the cyclic dep using defer()
            this.reset.next();
            this.sock.next(that.state.spec);
        })

        if (this.genRef.current) {
            const specGen$ = fromEvent(this.genRef.current, "click");
            specGen$.pipe(
                switchMap(evt => ajax.getJSON("/generate")),
                tap(evt => console.log("Generate", evt))
            ).subscribe(json => {
                this.setState({
                    ...this.state,
                    spec: JSON.stringify(json, null, 2)
                })
            })
        }
    }

    updateSpec(evt) {
        const spec = evt.target.value;
        this.persistSpec(spec);
    }

    persistSpec(spec) {
        localStorage.setItem("spec", spec);
        this.setState({
            ...this.state,
            spec
         });
    }

    changeSolver() {
        const { spec } = this.state;
        const parsed = JSON.parse(spec);
        if (parsed.solver === "mcts")
            parsed.solver = "guillotineFF";
        else
            parsed.solver = "mcts";

        const newSpec = JSON.stringify(parsed, null, 2);
        this.persistSpec(newSpec);
    }

    generateItems(count) {
        const { spec } = this.state;
        const parsed = JSON.parse(spec);
        var i=0;
        while (i<count) {
            const width = Math.ceil(Math.random() * 50);
            const height = Math.ceil(Math.random() * 50);

            if (width < 10 || height < 10)
                continue;

            if (width*height < 200) {
                continue;
            }

            parsed.items.push({
                width,
                height,
            })

            i++;
        }

        const newSpec = JSON.stringify(parsed, null, 2);
        this.persistSpec(newSpec);
    }

    render() {
        const time = new Date(this.state.time).toLocaleTimeString();
        const { spec } = this.state;
        const onChange = this.updateSpec.bind(this)

        // TODO fix button layout
        return html`
            <div>
            <div class="container">
            <aside class="menu">
                <button class="button" ref=${this.genRef}>Default Spec</button>
                <button class="button" onClick=${() => this.changeSolver()} >Next Solver</button>
                <button class="button" onClick=${() => this.generateItems(10)}>Generate Items (10)</button>
            </aside>
            <div class="tile is-ancestor">
                <div class="tile">
                    <div class="tile is-parent is-vertical">
                        <div class="box tile is-child">
                            <textarea class="textarea" style="width:100%; height:100%" rows="20" ref=${this.specRef} onChange=${onChange}>${spec}</textarea>
                        </div>
                    </div>
                    <div class="tile is-parent is-vertical is-6">
                        <div class="box tile is-child">
                            <div class="field">
                                <${SolutionBox} width=400 height=400 time=${time} source=${this.solution}/>
                            </div>
                            <div class="field">
                                <button class="button" ref=${this.buttonRef}>Go</button>
                            </div>
                            <${ProgressBar} source=${this.progress} />
                        </div>
                        <div class="box tile is-child">
                            <${History} source=${this.history} />
                        </div>
                    </div>
                </div>
            </div>
            </div>
            </div>
        `;
    }
}

const app = html`<${App} />`
render(app, document.getElementById('app'));